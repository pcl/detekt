package io.gitlab.arturbosch.detekt.rules.bugs

import io.gitlab.arturbosch.detekt.api.*
import io.gitlab.arturbosch.detekt.rules.hasAnnotation
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType

/**
 * Turn on this rule to flag Spring beanFunctions that make calls to other bean definition
 * methods defined within the same bean.
 *
 * These self-referencing calls can lead to unexpected creation of additional beanFunctions,
 * as such calls bypass the Spring-created proxies in many cases.
 *
 * <noncompliant>
 * @Configuration
 * class Foo {
 *     @Bean
 *     fun bikeFactory() : Factory<Bike> {
 *         return factoryBuilderProvider().newBuilder(Bike::class).build();
 *     }
 *
 *     @Bean
 *     fun skiFactory() : Factory<Ski> {
 *         return factoryBuilderProvider().newBuilder(Ski::class).build();
 *     }
 *
 *     @Bean
 *     fun factoryBuilder() : FactoryBuilder {
 *         // This will be invoked three times -- once to create the @Bean (as it's not declared @Lazy),
 *         // once from bikeFactory(), and once from skiFactory(). To avoid this, bikeFactory() and
 *         // skiFactory() should take a FactoryBuilder as a parameter, and rely on autowiring.
 *         return FactoryBuilder()
 *     }
 * }
 * </noncompliant>
 *
 * @author Patrick Linskey
 */
class SpringBeanSelfMethodInvocation(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(javaClass.simpleName,
            Severity.Defect,
            "Detected Spring bean methods that call other bean methods defined in the same class. These " +
                    "calls escape the Spring bean boundary, and likely result in duplicate beanFunctions being " +
                    "created. Self-defined beanFunctions should be injected as method parameters or handled as " +
                    "fields instead",
            Debt.TWENTY_MINS)

    private val beanFunctions = mutableListOf<KtElement>()
    private val beanProperties = mutableListOf<KtProperty>()
    private val candidateCalls = mutableListOf<KtCallExpression>()
    private val candidateAccessors = mutableListOf<KtPropertyAccessor>()

    // TODO filter to classes that are annotated Component etc.

    override fun visitClassBody(classBody: KtClassBody) {

        beanFunctions.clear()
        beanProperties.clear()
        candidateCalls.clear();
        val visitor = object : DetektVisitor() {

            override fun visitProperty(property: KtProperty) {
                super.visitProperty(property)
                if (property.hasAnnotation("Bean") || property.hasAnnotation("org.springframework.context.annotation.Bean")) {
                    //beanProperties.add(property)
                    beanFunctions.add(property)
                }
            }

            override fun visitNamedFunction(func: KtNamedFunction) {
                super.visitNamedFunction(func)
                if (func.hasAnnotation("Bean") || func.hasAnnotation("org.springframework.context.annotation.Bean")) {
                    beanFunctions.add(func)
                }
            }

            override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
                super.visitPropertyAccessor(accessor)
                candidateAccessors.add(accessor)
            }

            override fun visitExpression(expression: KtExpression) {
                super.visitExpression(expression)
            }

            override fun visitReferenceExpression(expression: KtReferenceExpression) {
                super.visitReferenceExpression(expression)

                if (expression.parent is KtDotQualifiedExpression && expression.parent.firstChild.text == "this") { // ##### should check that it's in the form 'this.expression', not 'this.foo.bar.expression'
                    System.err.println("### this==true")
                } else {
                    beanFunctions.add(expression)
                    if (expression.text.contains("beanDefinedInSelfByPropA")) {
                        System.err.println("##### " + expression.text + ": " + expression::class)
                        System.err.println("##### ${expression.parent.text}: " + expression.parent::class)
                    }
                }
            }

            override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression) {
                super.visitCallableReferenceExpression(expression)
            }

            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                candidateCalls.add(expression)
            }
        }

        visitor.visitClassBody(classBody)

        candidateAccessors.forEach {
            checkForSelfBeanCalls(it)
        }
        candidateCalls.forEach {
            checkForSelfBeanCalls(it)
        }
    }

    private fun checkForSelfBeanCalls(element: KtElement) {
        if (element.parent is KtDotQualifiedExpression) {
            // If this is a dot-qualified expression, filter out calls to things other than 'this'.
            // This is hackable, since we don't check for capturing 'this'. That's ok for now.
            if ((element.parent as KtDotQualifiedExpression).firstChild.text != "this") {
                return
            }
        }

        // Find any KtNameReferenceExpresssions in the call
        System.err.println("--- " + element.text)
        System.err.println(element.parent.text)
        element.forEachDescendantOfType<KtNameReferenceExpression> { referenceExpression ->
            beanFunctions.forEach { bean ->
                System.err.println("  it: ${referenceExpression.text}; bean: ${bean.name}")

                // If a bean function matches a call at this point, it must be a self invocation. Flag it.
                if (referenceExpression.text == bean.name) {
                    report(CodeSmell(issue, Entity.from(referenceExpression),
                        "A bean should not access other beanFunctions defined by the same class."))
                }
            }
        }
    }
}
