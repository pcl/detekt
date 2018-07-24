package io.gitlab.arturbosch.detekt.rules.naming

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.DetektVisitor
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

/**
 * This rule reports occurrences of name shadowing within a file. Name shadowing leads to surprising bugs, especially
 * as a file is maintained over time.
 *
 * <noncompliant>
 * class A {
 *     val foo: String?
 *
 *     fun doThings(val foo: String) { // this shadows the class-level name
 *         ...
 *     }
 * }
 *
 * fun functionWithShadowedNames() {
 *     val foo: String?
 *
 *     if (<condition>) {
 *         val foo: String? // this shadows the name in the outer scope
 *     }
 * }
 * </noncompliant>
 *
 * <compliant>
 * class A {
 *     val foo: String?
 *
 *     fun doThings(val bar: String) { // parameter name differs from the class member value
 *         ...
 *     }
 * }
 *
 * fun functionWithoutShadowedNames() {
 *     val foo: String?
 *
 *     if (<condition>) {
 *         val bar: String? // this does not shadow the name in the outer scope
 *     }
 * }
 * </compliant>
 *
 * @configuration allowImplicitItShadows - Allow implicit <code>it</code> variables to be shadowed (default: "false")
 *
 * @active since v1.0.0
 *
 * @author Patrick Linskey
 */
class NoNameShadowing(config: Config = Config.empty) : Rule(config) {

	private val allowImplicitItShadows = config.valueOrDefault("allowImplicitItShadows", false)

	private val _errors = mutableListOf<PsiErrorElement>()

	/**
	 * The errors that were seen while visiting the file. Exposed so we can validate
	 * that our test cases compile as expected.
	 */
	internal val errors: List<PsiErrorElement>
		get() = _errors

	override val issue: Issue = Issue("NoNameShadowing",
			Severity.Maintainability,
			"Name shadowing is not allowed.",
			Debt.FIVE_MINS)

	override fun visitFile(file: PsiFile?) {
		if (file != null) {
			val scopes = mutableSetOf<Scope>()
			var currentScope = Scope(file, null)
			scopes.add(currentScope)

			fun inScope(decl: PsiElement, block: () -> Unit) {
				val oldScope = currentScope
				currentScope = Scope(decl, oldScope)
				scopes.add(currentScope)
				try {
					block()
				} finally {
					currentScope = oldScope
				}
			}

			file.accept(object : DetektVisitor() {
				override fun visitParameter(parameter: KtParameter) {
					currentScope.register(parameter.name, parameter)
					super.visitParameter(parameter)
				}

				override fun visitProperty(property: KtProperty) {
					currentScope.register(property.name, property)
					super.visitProperty(property)
				}

				override fun visitDestructuringDeclarationEntry(multiDeclarationEntry: KtDestructuringDeclarationEntry) {
					// We don't need to support 'val ((foo, _), _)', as Kotlin only allows one level of destructuring
					// on a line
					currentScope.register(multiDeclarationEntry.name, multiDeclarationEntry)
					super.visitDestructuringDeclarationEntry(multiDeclarationEntry)
				}

				override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
					if (expression is KtNameReferenceExpression && "it".equals(expression.getReferencedName())) {
						// If we find an 'it' reference in a scope, add it to the scope if and only if the scope itself
						// doesn't already contain an 'it' name. This catches implicit 'if' values, and also tolerates
						// some code blocks that won't actually compile. That's ok, since the compiler itself will
						// prevent those from manifesting.
						if (!allowImplicitItShadows && "it" !in currentScope.scopeElementsByName) {
							currentScope.register("it", expression)
						}
					}
					super.visitSimpleNameExpression(expression)
				}

				override fun visitLabeledExpression(expression: KtLabeledExpression) {
					currentScope.register(expression.getLabelName(), expression)
					super.visitLabeledExpression(expression)
				}

				override fun visitNamedFunction(function: KtNamedFunction) {
					currentScope.register(function.name, function) // register function name in outer scope, then nest
					inScope(function) {
						super.visitNamedFunction(function)
					}
				}

				override fun visitClassOrObject(classOrObject: KtClassOrObject) {
					inScope(classOrObject) {
						super.visitClassOrObject(classOrObject)
					}
				}

				override fun visitForExpression(expression: KtForExpression) {
					inScope(expression) {
						super.visitForExpression(expression)
					}
				}

				override fun visitBlockExpression(expression: KtBlockExpression) {
					inScope(expression) {
						super.visitBlockExpression(expression)
					}
				}

				override fun visitCatchSection(catchClause: KtCatchClause) {
					inScope(catchClause) {
						super.visitCatchSection(catchClause)
					}
				}

				override fun visitErrorElement(element: PsiErrorElement?) {
					if (element != null) {
						_errors.add(element)
					}
					super.visitErrorElement(element)
				}
			})

			scopes.forEach { it.gatherConflicts() }
			scopes.forEach { it.reportConflicts(::reportExpression) }
		}

		super.visitFile(file)
	}

	private fun reportExpression(elem: NamedElement) {
		report(CodeSmell(issue, Entity.from(elem.expr), "Names should be unique within a single file"))
	}

	private class Scope(val scopeContextElement: PsiElement, val parent: Scope?) {
		val scopeElementsByName = mutableMapOf<String, MutableList<NamedElement>>()
		val scopeConflicts = mutableSetOf<NamedElement>()

		fun register(name: String?, element: KtExpression) {
			if (isScopeless(scopeContextElement)) {
				parent?.register(name, element)
			} else {
				if (name != null) {
					// non-named elements (e.g., destructuring elements) will be handled in other visitor calls
					if (!name.equals("_")) { // _ is not accessible; shadowing is fine here
						scopeElementsByName.getOrPut(name, { mutableListOf() }).add(NamedElement(name, element))
					}
				}
			}
		}

		private fun isScopeless(scopeContextElement: PsiElement): Boolean {
			// 'companion object {}' and some others should share their parent scopes
			return scopeContextElement is KtObjectDeclaration && scopeContextElement.isCompanion()
		}

		private fun testDescendentScopeElement(element: NamedElement) {
			if (element.name in scopeElementsByName) {
				// add a conflict record for the conflicts from this scope...
				scopeConflicts.addAll(scopeElementsByName[element.name]!!)

				// ... and add the descendent record too
				scopeConflicts.add(element)
			}
			parent?.testDescendentScopeElement(element)
		}

		fun gatherConflicts() {
			scopeElementsByName.values.forEach {
				// If we have intra-scope name shadowing, register the conflicts. This can happen
				// if we have a field and a function with the same name, or in a secondary constructor
				if (it.size > 1 && filterOutSecondaryPrimaryConflicts(it).size > 1) {
					scopeConflicts.addAll(it)
				}

				// Check this element against parent scopes
				it.forEach {
					parent?.testDescendentScopeElement(it)
				}
			}
		}

		private fun filterOutSecondaryPrimaryConflicts(intraScopeConflicts: List<NamedElement>):
				List<NamedElement> {
			val primaryConstructorParams = intraScopeConflicts.filter {
				it.expr.parent?.parent is KtPrimaryConstructor
			}
			if (primaryConstructorParams.size == 1) { // should always be 0 or 1; > 1 should be a compile error
				val nonSecondary = intraScopeConflicts.filterNot {
					it.expr is KtParameter && it.expr.parent is KtParameterList
							&& it.expr.parent?.parent is KtSecondaryConstructor
				}
				if (nonSecondary.size == 1) {
					// the only thing remaining is the primary; no conflicts here
					return listOf()
				} else {
					// nonSecondary contains the primary, so it'll report properly. This means that the
					// report won't contain the secondary conflicts; if the developer resolves the conflict
					// by changing the name of the primary constructor parameter, the secondaries will then
					// become conflicts with whatever the rest of the conflict is.
					return nonSecondary
				}
			} else {
				return intraScopeConflicts
			}
		}

		fun reportConflicts(report: (NamedElement) -> Unit) {
			// Issue reports for anything that conflicts within this scope
			scopeConflicts.sortedBy { it.name }.forEach { report(it) }
		}
	}

	private class NamedElement(val name: String, val expr: KtExpression)
}
