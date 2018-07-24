package io.gitlab.arturbosch.detekt.rules.naming

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.DetektVisitor
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Location
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.rules.companionObject
import io.gitlab.arturbosch.detekt.rules.isInternal
import io.gitlab.arturbosch.detekt.rules.isOverridden
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter
import org.jetbrains.kotlin.psi.psiUtil.isProtected
import org.jetbrains.kotlin.psi.psiUtil.isPublic

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
 * @active since v1.0.0
 *
 * @author Patrick Linskey
 */
class NoNameShadowing(config: Config = Config.empty) : Rule(config) {

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
					val isTypeReferenceParam = parameter.parent is KtParameterList
							&& parameter.parent?.parent is KtFunctionType
							&& parameter.parent?.parent?.parent is KtTypeReference
					if (!isTypeReferenceParam) {
						currentScope.register(parameter.name, parameter)
					}
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

				override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
					inScope(lambdaExpression) {
						super.visitLambdaExpression(lambdaExpression)
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
			scopes.forEach { it.reportConflicts() }
		}

		super.visitFile(file)
	}

	private inner class Scope(val scopeContextElement: PsiElement, val parent: Scope?) {
		val scopeElementsByName = mutableMapOf<String, MutableList<NamedElement>>()
		val scopeConflicts = mutableSetOf<NamedElement>()
		val isCompanionObjectScope: Boolean

		init {
			if (scopeContextElement is KtClassOrObject) {
				val containingClass = scopeContextElement.containingClass()
				isCompanionObjectScope = containingClass != null
						&& containingClass.companionObject() == scopeContextElement
			} else {
				isCompanionObjectScope = false
			}
		}

		fun register(name: String?, element: KtExpression) {
			if (isScopeless()) {
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

		private fun isScopeless(): Boolean {
			// 'companion object {}' should share their parent scopes
			return scopeContextElement is KtObjectDeclaration && scopeContextElement.isCompanion()
		}

		private fun testDescendentScopeElement(element: NamedElement) {
			if (element.name in scopeElementsByName) {
				// add a conflict record for the conflicts from this scope...
				scopeConflicts.addAll(scopeElementsByName[element.name]!!)

				// ... and add the descendent record too
				scopeConflicts.add(element)
			}

			// don't propagate companion object function vars and vals
			if (!(isCompanionObjectScope && element.isLocal())) {
				parent?.testDescendentScopeElement(element)
			}
		}

		fun gatherConflicts() {
			fun eitherPair(a: NamedElement, b: NamedElement, test: (NamedElement, NamedElement) -> Boolean): Boolean {
				return test(a, b) || test(b, a)
			}

			fun addIntraScopeConflict(candidate: NamedElement, conflict: NamedElement) {
				if (candidate == conflict) {
					return
				}

				// Filter out some special cases:
				//   - non-private properties and functions with the same name
				//   - all function overrides
				//   - secondary constructors with the same param names as the vals defined in the primary.
				// This leaves:
				// 	 - non-property fields
				//   - private properties and functions
				//   - block-defined (e.g., local) functions
				if (eitherPair(candidate, conflict,
								{ a, b -> a.isPrimaryConstructorParameter() && b.isSecondaryConstructorParameter()})) {
					return
				}

				if (eitherPair(candidate, conflict, { a, b -> a.isExternallyVisiblePropOrFun() &&
							b.isPrimaryConstructorParameter() && b.isExternallyVisiblePropOrFun() })) {
					return
				}

				if (candidate.isExternallyVisiblePropOrFun() && conflict.isExternallyVisiblePropOrFun()) {
					return
				}

				if (candidate.isFunction() && conflict.isFunction()) {
					return
				}

				scopeConflicts.add(conflict)
				scopeConflicts.add(candidate)
			}

			scopeElementsByName.forEach { (_, intraScopeConflictItems) ->
				// If we have intra-scope name shadowing, register the conflicts.
				if (intraScopeConflictItems.size > 1) {
					// This is O(n^2) right now. We could reduce that to O(n!) or so, but n is likely to be
					// small in all reasonable cases, and the Set-ness of scopeConflicts de-duplicates things.
					intraScopeConflictItems.forEach { candidate ->
						intraScopeConflictItems.forEach { conflict -> addIntraScopeConflict(candidate, conflict) }
					}
				}

				// Check this element against parent scopes
				for (it in intraScopeConflictItems) {
					if (it.isFunction() && (it.expr as KtFunction).isOverridden()) {
						continue
					}
					parent?.testDescendentScopeElement(it)
				}
			}
		}

		fun reportConflicts() {
			// Issue reports for anything that conflicts within this scope
			scopeConflicts.sortedWith(Comparator { a, b ->
				if (a.name == b.name) {
					Integer.compare(a.location().text.start, b.location().text.start)
				} else {
					a.name.compareTo(b.name)
				}
			}).forEach {
				report(CodeSmell(issue, Entity.from(it.expr), "Names should be unique within a single file"))
			}
		}
	}

	private class NamedElement(val name: String, val expr: KtExpression) {
		fun location() = Location.from(expr)

		fun isPrimaryConstructorParameter() = expr.parent?.parent is KtPrimaryConstructor

		fun isSecondaryConstructorParameter() = expr is KtParameter && expr.parent is KtParameterList
				&& expr.parent?.parent is KtSecondaryConstructor

		fun isExternallyVisiblePropOrFun(): Boolean {
			return (expr is KtFunction
					|| expr is KtProperty
					|| isPrimaryConstructorParameter() && (expr as KtParameter).isPropertyParameter())
					&& expr is KtModifierListOwner && (expr.isPublic || expr.isInternal() || expr.isProtected())
		}

		fun isFunction() = expr is KtFunction

		fun isLocal() = expr is KtProperty && expr.isLocal
				|| expr is KtParameter && !expr.isPropertyParameter()
	}
}
