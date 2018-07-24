package io.gitlab.arturbosch.detekt.rules.naming

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Finding
import io.gitlab.arturbosch.detekt.test.lint
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.subject.SubjectSpek

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

					fun func1(parameterName: String) {
						val fieldName: String?
						{
							val blockScopedFieldName: String?
						}
					}

					fun func2(parameterName: String) {
						val fieldName: String?
						{
							val blockScopedFieldName: String?
						}
					}

					fun siblingAnonymousBlocks() {
						{
							val blockScopedFieldName: String?
						}
						{
							val blockScopedFieldName: String?
						}
					}

					fun siblingTryBlocks() {
						try {
							val tryBlockScopedFieldName: String?
						} finally {
						}

						try {
							val tryBlockScopedFieldName: String?
						} finally {
						}
					}

					fun siblingClosures() {
						listOf<String>().filter { item -> System.out.println(item) }
						listOf<String>().filter { item -> System.out.println(item) }
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
				class A(val foo: String) {
					constructor(foo: String, bar: String): this(foo) {
					}
				}""")
			assertThat(findings).hasSize(0)
		}

		it("allows secondary constructor shadowing of params") {
			val findings = subject.lintAndAssertCompilation("""
				class A(foo: String) {
					constructor(foo: String, bar: String): this(foo) {
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

		it("rejects variables and nested functions with the same name") {
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

		it("allows overridden functions") {
			val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo() {
					}

					fun foo(name: String) {
					}

					fun foo(age: Int) {
					}
				}""")
			assertThat(findings).hasSize(0)
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
			it("rejects class non-property param shadowing constructor-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				class A(name: String?) {
					val name: String?
				}""")
				assertThat(findings).hasSize(2)
			}

			it("allows public function name shadowing constructor-scoped property") {
				val findings = subject.lintAndAssertCompilation("""
				class A(val name: String?) {
					fun name() {
					}
				}""")
				assertThat(findings).hasSize(0)
			}

			it("rejects private function name shadowing constructor-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				class A(val name: String?) {
					private fun name() {
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects public function name shadowing private constructor-scoped property") {
				val findings = subject.lintAndAssertCompilation("""
				class A(private val name: String?) {
					fun name() {
					}
				}""")
				assertThat(findings).hasSize(2)
			}

			it("rejects public function name shadowing constructor-scoped param") {
				val findings = subject.lintAndAssertCompilation("""
				class A(name: String?) {
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

			it("allows public companion-object-scoped property shadowing constructor-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				class A(val name: String?) {
					companion object {
						val name: String?
						fun name() { }
					}
				}""")
				assertThat(findings).hasSize(0)
			}

			it("rejects private companion-object-scoped property shadowing constructor-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				class A(val name: String?) {
					companion object {
						private val name: String?
						private fun name() { }
					}
				}""")
				assertThat(findings).hasSize(3)
			}
		}

		describe("class-scoped") {
			it("allows function name shadowing class-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					val name: String?
					fun name() {
					}
				}""")
				assertThat(findings).hasSize(0)
			}

			it("rejects private function name shadowing class-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					val name: String?
					private fun name() {
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

			it("allows public companion-object-scoped property shadowing class-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					val name: String?
					companion object {
						val name: String?
					}
				}""")
				assertThat(findings).hasSize(0)
			}

			it("rejects private companion-object-scoped property shadowing class-scoped name") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					val name: String?
					companion object {
						private val name: String?
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

			it("allows shadowing parameter names in function-type return declarations") {
				val findings = subject.lintAndAssertCompilation("""
				class A {
					fun foo(): (name: String) -> Unit { // technically the name isn't needed here, but it's out of scope
						return { name -> System.out.println(name) }
					}

					fun bar(): (name: String) -> Unit { // technically the name isn't needed here, but it's out of scope
						val name = "aoeu"
						return { n -> System.out.println(name + n) }
					}
				}""")
				assertThat(findings).hasSize(0)
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

		it("allows nested-scope and same-scope apparent conflicts when implementing / overriding in an inner class") {
			val findings = subject.lintAndAssertCompilation("""
			class A {
				val close = "aoeu" // apparent conflict with AutoCloseable function name

				fun foo() {
					val anonymousObject = object : java.io.AutoCloseable {
						override fun close() { }
					}
				}
			}

			class B {
				fun foo() {
					val close = "aoeu" // apparent conflict with AutoCloseable function name
					val anonymousObject = object : java.io.AutoCloseable {
						override fun close() { }
					}
				}
			}""")
			assertThat(findings).hasSize(0)
		}

		it("rejects nested-scope and same-scope real conflicts with function non-override functions in an inner class") {
			val findings = subject.lintAndAssertCompilation("""
			class A {
				val name = "aoeu"  // real conflict with non-override function

				fun foo() {
					val anonymousObject = object {
						fun name() { }
					}
				}
			}

			class B {
				fun foo() {
					val name = "aoeu"  // real conflict with non-override function
					val anonymousObject = object {
						fun name() { }
					}
				}
			}""")
			assertThat(findings).hasSize(4)
		}

		describe("companion objects") {
			it("allows companion object functions to use constructor params") {
				val findings = subject.lintAndAssertCompilation("""
				class A(val name1: String, _name2: String) {
					val name2 = _name2

					companion object {
						fun newInstance(name1: String, name2: String): A {
							val theCopy = A(name1)
							theCopy.name2 = name2
							return theCopy
						}

						fun copy(other: A): A {
							val name1 = other.name1
							val name2 = other.name2
							return newInstance(name1, name2)
						}
					}
				}""")
				assertThat(findings).hasSize(0)
			}

			it("rejects companion object nested function conflicts") {
				val findings = subject.lintAndAssertCompilation("""
				class A(val name1: String, _name2: String) {
					val name2 = _name2

					companion object {
						fun foo() {
							fun name1() { }
							fun name2() { }
						}
					}
				}""")
				assertThat(findings).hasSize(4)
			}
		}

		/* TODO It would be nice to figure out how to discover implicit 'it' definitions.
		   		They appear in the AST inside KtSimpleNameExpression instances, but there is not enough information
		   		in the AST to distinguish between a lambda with no arguments and one with a single implicit argument.
		   		Perhaps there's a later stage of processing in which the lambda is enriched with implied arguments.
		describe("implicit 'it' params") {
			it("rejects 'it' in nested blocks by default") {
				val findings = subject.lintAndAssertCompilation("""
					class A {
						fun foo() {
							listOf(listOf("a"), listOf("b")).forEach {
								it.forEach { it ->
									val msg1 = it.toLowerCase() // The error should reference 'it', not 'msg1'
									val msg2 = "${"$"}it" // The error should reference 'it', not 'msg2'
								}
							}
						}
					}""")
				assertThat(findings).hasSize(3)
				findings.forEach { finding ->
					finding.references.forEach {
						assertThat(it.name).isEqualTo("it")
					}
				}
			}

			it("allows 'it' in nested blocks when configured") {
				val configured = NoNameShadowing(TestConfig(mapOf("allowImplicitItShadows" to "true")))
				val findings = configured.lintAndAssertCompilation("""
					class A {
						fun foo() {
							listOf(listOf("a"), listOf("b")).forEach {
								it.forEach {
									val msg1 = it.toLowerCase()
									val msg2 = "${"$"}it"
								}
							}
						}
					}""")
				assertThat(findings).hasSize(0)
			}

			it("allows 'it' in nested non-implicit-it blocks") {
				val findings = subject.lintAndAssertCompilation("""
					class A {
						fun foo() {
							listOf(listOf("a"), listOf("b")).forEach { // 'forEach' takes a lambda with an implicit 'it'
								val foo = it
								this.exec { // 'exec' takes a lambda with zero arguments
									val bar = it
								}
							}
						}

						fun exec(func: () -> Unit) {
						}
					}""")
				assertThat(findings).hasSize(0)
			}
		}*/
	}
})

/* 'value' names here should be considered as separate branches
35:    private var selectedModule: Module?
36:         set(value) {
37:             module.component.selectedModule = value
38:         }
39:
40:     private var selectedPath by Delegates.observable(PathBuilder.ROOT) { _, _, value ->
 */

/* Extension function should be considered external
xdescribe @ /Users/pcl/src/detekt/analysis-projects/spek/spek-dsl/common/src/main/kotlin/org/spekframework/spek2/style/specification/specificationStyle.kt

22:         delegate.createSuite(description, skip, body)
23:     }
24:
25:     @Synonym(SynonymType.GROUP, excluded = true)
26:     @Descriptions(Description(DescriptionLocation.VALUE_PARAMETER, 0))
27:     fun xdescribe(description: String, reason: String = "", body: Suite.() -> Unit) {
28:         delegate.createSuite(description, Skip.Yes(reason), body)
--
--
69:     createSuite(description, skip, body)
70: }
71:
72: @Synonym(SynonymType.GROUP)
73: @Descriptions(Description(DescriptionLocation.VALUE_PARAMETER, 0))
74: fun GroupBody.xdescribe(description: String, reason: String = "", body: Suite.() -> Unit) {
75:     createSuite(description, Skip.Yes(reason), body)
 */

/* Override contains param names; these should be excluded
// ##### consider allowing parameters of exposed functions to shadow other exposed props / functions also, since
// ##### they might be used as named params and thus are meaningful parts of the signature
defaultCachingMode @ /Users/pcl/src/detekt/analysis-projects/spek/spek-runtime/common/src/main/kotlin/org/spekframework/spek2/runtime/Collectors.kt
13:     val root: GroupScopeImpl,
14:     private val lifecycleManager: LifecycleManager,
15:     private val fixtures: FixturesAdapter,
16:     override val defaultCachingMode: CachingMode
17: ) : Root {
18:
19:     private val ids = linkedMapOf<String, Int>()
--
--
37:         lifecycleManager.addListener(listener)
38:     }
39:
40:     override fun group(description: String, skip: Skip, defaultCachingMode: CachingMode, body: GroupBody.() -> Unit) {
41:         val group = GroupScopeImpl(
42:             idFor(description),
43:             root.path.resolve(description),


name @ /Users/pcl/src/detekt/analysis-projects/spek/spek-runtime/common/src/main/kotlin/org/spekframework/spek2/runtime/scope/Path.kt
5:
6: expect class Path {
7:     val parent: Path?
8:     val name: String
9:     fun resolve(name: String): Path
10:     fun serialize(): String
11:     fun isParentOf(path: Path): Boolean
12: }
 */

/* Test that inner class constructors can't re-use outer class exposed var
destructor @ /Users/pcl/src/detekt/analysis-projects/spek/spek-runtime/common/src/main/kotlin/org/spekframework/spek2/runtime/lifecycle/MemoizedValueAdapter.kt
10: sealed class MemoizedValueAdapter<T>(
11:     val factory: () -> T,
12:     val destructor: (T) -> Unit
13: ) : ReadOnlyProperty<Any?, T>, LifecycleListener {
14:
15:     protected sealed class Cached<out T> {
--
--
35:
36:     class GroupCachingModeAdapter<T>(
37:         factory: () -> T,
38:         destructor: (T) -> Unit
39:     ) : MemoizedValueAdapter<T>(factory, destructor) {
40:
41:         private val stack = mutableListOf<Cached<T>>()
 */

/*
name @ /Users/pcl/src/detekt/analysis-projects/spek/spek-runtime/jvm/src/main/kotlin/org/spekframework/spek2/runtime/scope/Path.kt
4: import java.util.*
5: import kotlin.reflect.KClass
6:
7: actual data class Path(actual val name: String, actual val parent: Path?) {
8:     private val serialized by lazy {
9:         serialize(this)
10:     }
--
--
17:         encode(name)
18:     }
19:
20:     actual fun resolve(name: String) = Path(name, this)
21:
22:     actual fun isParentOf(path: Path): Boolean {
23:         var current: Path? = path
 */

/* Setters? Perhaps if the only statement in the block is the assignment?
passParentEnvs @ /Users/pcl/src/detekt/analysis-projects/spek/spek-ide-plugin/intellij-common/src/main/kotlin/org/spekframework/intellij/base.kt
35:
36:     private var workingDirectory: String? = null
37:     private var envs = mutableMapOf<String, String>()
38:     private var passParentEnvs: Boolean = false
39:     private var programParameters: String? = null
40:
41:     var path: Path = PathBuilder.ROOT
--
--
56:
57:     override fun isPassParentEnvs() = passParentEnvs
58:
59:     override fun setPassParentEnvs(passParentEnvs: Boolean) {
60:         this.passParentEnvs = passParentEnvs
61:     }
62:
 */
fun NoNameShadowing.lintAndAssertCompilation(content: String): List<Finding> {
	val findings = this.lint(content)
	assertThat(this.errors).isEqualTo(listOf<PsiErrorElement>())
	return findings
}
