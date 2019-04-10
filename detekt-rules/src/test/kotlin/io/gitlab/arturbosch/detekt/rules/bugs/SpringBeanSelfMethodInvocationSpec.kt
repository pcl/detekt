package io.gitlab.arturbosch.detekt.rules.bugs

import io.gitlab.arturbosch.detekt.test.lint
import org.assertj.core.api.Assertions.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

// TODO test beans defined in superclasses
class SpringBeanSelfMethodInvocationSpec : Spek({

    describe("SpringBeanSelfMethodInvocation rule") {

        describe("implicit self func") {
            val code = """
			import org.springframework.context.annotation.Bean
			import org.springframework.context.annotation.Component

            @Component
			class Class {
				fun func1() : String {
                    return beanDefinedInSelfByFunc()
				}

				@Bean
				fun beanDefinedInSelfByFunc() : String {
                    return "foo"
				}
			}
		"""

            val findings = SpringBeanSelfMethodInvocation().lint(code)
            assertThat(findings).hasSize(1)
        }

        describe("explicit self func") {
            val code = """
			import org.springframework.context.annotation.Bean
			import org.springframework.context.annotation.Component

            @Component
			class Class {
				fun func1() : String {
                    return this.beanDefinedInSelfByFunc()
				}

				@Bean
				fun beanDefinedInSelfByFunc() : String {
                    return "foo"
				}
			}
		"""

            val findings = SpringBeanSelfMethodInvocation().lint(code)
            assertThat(findings).hasSize(1)
        }

        describe("implicit and explicit self props and funcs") {
            val code = """
			import org.springframework.context.annotation.Bean
			import org.springframework.context.annotation.Component

            @Component
			class Class {
				val prop1 = beanDefinedInSelfByFunc()
				val prop2 = this.beanDefinedInSelfByFunc()
                val prop3 = beanDefinedInSelfByProp
                val prop4 = this.beanDefinedInSelfByProp

                // TODO getters and setters

                // raw constructor calls
                beanDefinedInSelfByFunc()
                this.beanDefinedInSelfByFunc

				fun beanFunc1() : String {
                    beanProp2
				}

                @Bean
                val beanDefinedInSelfByProp = "bar"

                @Bean
                fun beanDefinedInSelfByFunc() : String {
                    "foo"
                }
			}
		"""

            val findings = SpringBeanSelfMethodInvocation().lint(code)
            assertThat(findings).hasSize(6)
        }

        describe("explicit self prop") {
            val code = """
			import org.springframework.context.annotation.Bean
			import org.springframework.context.annotation.Component

            @Component
			class Class {
				fun beanFunc1() : String {
                    return this.beanDefinedInSelfByFunc()
				}

				@Bean
				fun beanDefinedInSelfByFunc() : String {
                    return "foo"
				}
			}
		"""

            val findings = SpringBeanSelfMethodInvocation().lint(code)
            assertThat(findings).hasSize(1)
        }

        describe("implicit and explicit self props and funcs in nested blocks and inner classes") {
            val code = """
			import org.springframework.context.annotation.Bean
			import org.springframework.context.annotation.Component

            @Component
			class Class {
				fun beanFunc() : Consumer<String> {
                    fun foo() {
                        beanDefinedInSelfByFunc()
                        val foo = beanDefinedInSelfByPropA
                    }

                    {
                        this.beanDefinedInSelfByFunc()
                        val foo = this.beanDefinedInSelfByPropA
                    }

                    return object : Consumer<String> {
                        fun accept(s: String) {
                            beanDefinedInSelfByFunc()
                            this.beanDefinedInSelfByFunc()
                        }
                    }
				}

                @Bean
                fun beanDefinedInSelfByPropA = "foo"

				@Bean
				fun beanDefinedInSelfByFunc() : String {
                    return "bar"
				}
			}
		"""

            val findings = SpringBeanSelfMethodInvocation().lint(code)
            assertThat(findings).hasSize(6)
        }

        describe("calls to another bean") {
            val code = """
			import org.springframework.context.annotation.Bean
			import org.springframework.context.annotation.Component

            @Component
			class Class {
				fun beanFunc() : Consumer<String> {
                    val other = Class()
                    other.beanDefinedInSelfByFunc() // this should be ok, since it's not a call to 'this'.
				}

				@Bean
				fun beanDefinedInSelfByFunc() : String {
                    return "foo"
				}
			}
		"""

            val findings = SpringBeanSelfMethodInvocation().lint(code)
            assertThat(findings).hasSize(0)
        }
    }
})
