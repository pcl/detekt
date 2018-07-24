package io.gitlab.arturbosch.detekt.rules.naming

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Finding
import io.gitlab.arturbosch.detekt.test.KtTestCompiler
import io.gitlab.arturbosch.detekt.test.TestConfig
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.subject.SubjectSpek
import kotlin.test.fail

class NoNameShadowingSpec : SubjectSpek<NoNameShadowing>({
	subject { NoNameShadowing(Config.empty) }

	describe("check no name shadowing") {

		it("has no shadowed names") {
			val findings = subject.lintAndAssertCompilation("""
				val fileName: String?

				class A(val constructorName: String) {
					val propertyName: String?

					fun foo(parameterName: String) {
						val fieldName: String?
						{
							val blockScopedFieldName: String?
						}
					}

					companion object {
						val companionName: String?
					}
				}""")
			assertThat(findings).hasSize(0)
		}

		it("allows shared names in separate scope branches") {
			val findings = subject.lintAndAssertCompilation("""
				class A(val constructorName: String) {
					val propertyName: String?

					fun foo(parameterName: String) {
						val fieldName: String?
						{
							val blockScopedFieldName: String?
						}
					}

					fun bar(parameterName: String) {
						val fieldName: String?
						{
							val blockScopedFieldName: String?
						}
						{
							val blockScopedFieldName: String?
						}
					}

					fun baz() {
						try {
							val tryBlockScopedFieldName: String?
						} finally {
						}

						try {
							val tryBlockScopedFieldName: String?
						} finally {
						}
					}
				}

				class B(val constructorName: String) {
					val propertyName: String?

					fun foo(parameterName: String) {
						val fieldName: String?
						{
							val blockScopedFieldName: String?
						}
					}

					companion object {
						val companionName: String?
					}
				}""")
			assertThat(findings).hasSize(0)
		}

		it("allows shared names in sibling catch blocks") {
			val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo() {
						try {
						} catch (ex: IOException) {
						} catch (ex: Exception) {
						}
					}
				}""")
			assertThat(findings).hasSize(0)
		}

		it("rejects shadowing in nested catch blocks") {
			val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo() {
						try {
						} catch (ex: IOException) {
							try {
							} catch (ex: Exception) {
							}
						}
					}
				}""")
			assertThat(findings).hasSize(2)
		}

		it("allows secondary constructor shadowing of vals") {
			val findings = subject.lintAndAssertCompilation("""
				class A(val fooo: String) {
					constructor(fooo: String, bar: String): this(foo) {
					}
				}""")
			assertThat(findings).hasSize(0)
		}

		it("allows secondary constructor shadowing of params") {
			val findings = subject.lintAndAssertCompilation("""
				class A(foooo: String) {
					constructor(foooo: String, bar: String): this(foo) {
					}
				}""")
			assertThat(findings).hasSize(0)
		}

		it("rejects 'it' in nested blocks by default") {
			val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo() {
						listOf(listOf("a"), listOf("b")).forEach {
							it.forEach {
								System.out.println(it)
							}
						}
					}
				}""")
			assertThat(findings).hasSize(2)
		}

		it("allows 'it' in nested blocks when configured") {
			val configured = NoNameShadowing(TestConfig(mapOf("allowImplicitItShadows" to "true")))
			val findings = configured.lintAndAssertCompilation("""
				class A {
					fun foo() {
						listOf(listOf("a"), listOf("b")).forEach {
							it.forEach {
								System.out.println(it)
							}
						}
					}
				}""")
			assertThat(findings).hasSize(0)
		}

		it("rejects nested functions shadowing other names") {
			val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo() {
						fun foo() {
						}
					}
				}

				class B(name: String) {
					fun bar() {
						fun foo() {
						}
					}

					companion object {
						val foo: String?
					}
				}""")
			assertThat(findings).hasSize(4)
		}

		it("rejects variables and functions with the same name") {
			val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo() {
						val bar = 3
						fun bar() {
						}
					}
				}""")
			assertThat(findings).hasSize(2)
		}

		it("allows inner classes with the same name") {
			val findings = subject.lintAndAssertCompilation("""
				class A {
					class Visitor { }
				}

				class B {
					class Visitor { }
				}""")
			assertThat(findings).hasSize(0)
		}

		describe("'for' loop scoping") {
			it("allows sibling 'for' loops to share loop variables") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo() {
						for (item in setOf<String>()) {
							val forBlockName: String?
						}

						for (item in setOf<String>()) {
							val forBlockName: String?
						}

						for ((key, item) in mapOf<String, String>()) {
						}

						for ((key, item) in mapOf<String, String>()) {
						}
					}
				}""")
				assertThat(findings).hasSize(0)
			}

			it("rejects 'for' loop variables that shadow parent context") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo() {
						val item: String?
						for (item in setOf<String>()) {
						}
						for ((key, item) in mapOf<String, String>()) {
						}
					}
				}""")
				assertThat(findings).hasSize(3)
			}
		}

		it("rejects labeled blocks that shadow") {
			val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo() {
						LABEL@ while (true) {
							LABEL@ while (true) {
							}
						}
						LABEL@ while (true) {
						}
					}
				}""")
			assertThat(findings).hasSize(3)
		}

		it("allows labeled blocks in siblings") {
			val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo() {
						LABEL@ while (true) {
						}
					}

					fun bar() {
						LABEL@ while (true) {
						}
					}
				}""")
			assertThat(findings).hasSize(0)
		}

		it("rejects lambdas with arguments that shadow") {
			val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo() {
						listOf<String>().forEach { foo -> System.out.println(foo) }
					}
				}""")
			assertThat(findings).hasSize(2)
		}

		describe("file-scoped name") {
			it("rejects constructor parameter shadowing file-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				val name: String?

				class A(val name: String) { }""")
				assertThat(findings).hasSize(2)
			}

			it("rejects class property shadowing file-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				val name: String?

				class A {
					val name: String?
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects function name shadowing file-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				val name: String?

				class A {
					fun name() {
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects nested function name shadowing file-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				val name: String?

				class A {
					fun foo() {
						fun name() {
						}
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects function parameter shadowing file-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				val name: String?

				class A {
					fun foo(name: String) {
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects function variable shadowing file-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				val name: String?

				class A {
					fun foo() {
						val name: String?
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects block-scoped property shadowing file-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				val name: String?

				class A {
					fun foo() {
						{
							val name: String?
						}
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects companion-object-scoped property shadowing file-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				val name: String?

				class A {
					companion object {
						val name: String?
					}
				}""")
				assertThat(findings).hasSize(2)
			}
		}

		describe("constructor-scoped") {
			it("rejects class property shadowing constructor-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				class A(val name: String?) {
					val name: String?
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects function name shadowing constructor-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				class A(val name: String?) {
					fun name() {
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects nested function name shadowing class-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				class A(val name: String?) {
					fun foo() {
						fun name() {
						}
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects function parameter shadowing constructor-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				class A(val name: String?) {
					fun foo(name: String) {
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects function variable shadowing constructor-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				class A(val name: String?) {
					fun foo() {
						val name: String?
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects block-scoped property shadowing constructor-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				class A(val name: String?) {
					fun foo() {
						{
							val name: String?
						}
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects companion-object-scoped property shadowing constructor-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				class A(val name: String?) {
					companion object {
						val name: String?
					}
				}""")
				assertThat(findings).hasSize(2)
			}
		}

		describe("class-scoped") {
			it("rejects function name shadowing class-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					val name: String?
					fun name() {
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects nested function name shadowing class-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					val name: String?
					fun foo() {
						fun name() {
						}
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects function parameter shadowing class-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					val name: String?
					fun foo(name: String) {
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects function variable shadowing class-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					val name: String?
					fun foo() {
						val name: String?
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects block-scoped property shadowing class-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					val name: String?
					fun foo() {
						{
							val name: String?
						}
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects companion-object-scoped property shadowing class-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					val name: String?
					companion object {
						val name: String?
					}
				}""")
				assertThat(findings).hasSize(2)
			}
		}

		describe("function parameter") {
			it("rejects function variable shadowing function parameter name") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					fun fooo(noame: String) {
						val noame: String?
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects nested function name shadowing function parameter name") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo(name: String) {
						fun foo() {
						}
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects block-scoped property shadowing function parameter name") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo(name: String) {
						{
							val name: String?
						}
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects companion-object-scoped property shadowing function parameter name") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo(name: String) {
					}

					companion object {
						val name: String?
					}
				}""")
				assertThat(findings).hasSize(2)
			}
		}

		describe("nested function") {
			it("rejects block-scoped property shadowing nested function name") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo() {
						val name: String?
						{
							val name: String?
						}
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects companion-object-scoped property shadowing nested function name") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo() {
						val name: String?
					}

					companion object {
						val name: String?
					}
				}""")
				assertThat(findings).hasSize(2)
			}
		}

		describe("function variable") {
			it("rejects block-scoped property shadowing nested function name") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo() {
						fun name() {
						}

						{
							val name: String?
						}
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects companion-object-scoped property shadowing nested function name") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo() {
						fun name() {
						}
					}

					companion object {
						val name: String?
					}
				}""")
				assertThat(findings).hasSize(2)
			}
		}

		describe("block variable") {
			it("rejects companion-object-scoped property shadowing block variable name") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo() {
						{
							val name: String?
						}
					}

					companion object {
						val name: String?
					}
				}""")
				assertThat(findings).hasSize(2)
			}
		}

		describe("tuple variables") {
			it("rejects tuple name shadowing function variable name") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo(val tname: String?) {
						val (tname, _) = Pair(1, 2)
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects sibling tuple names") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo() {
						val (a, b) = Pair(1, 2)
						val (a, b) = Pair(1, 2)
					}
				}""")
				assertThat(findings).hasSize(4)
			}

			it("allows _ shadowing in tuple names") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo() {
						val (_, _) = Pair(1, 2)
						val (_, _) = Pair(1, 2)
						try {
							val (_, _) = Pair(1, 2)
						} finally {
							val (_, _) = Pair(1, 2)
						}
					}
				}""")
				assertThat(findings).hasSize(0)
			}
		}
	}
})

fun NoNameShadowing.lintAndAssertCompilation(content: String): List<Finding> {
	val ktFile = KtTestCompiler.compileFromContent(content.trimIndent())
	this.visitFile(ktFile)
	assertThat(this.errors).isEqualTo(listOf<PsiErrorElement>())
	return this.findings
}
