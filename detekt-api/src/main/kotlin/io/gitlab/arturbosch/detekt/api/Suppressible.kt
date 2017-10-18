package io.gitlab.arturbosch.detekt.api

import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.preprocessor.typeReferenceName
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

/**
 * @author Artur Bosch
 */

/**
 * Checks if this psi element is suppressed by @Suppress or @SuppressWarnings annotations.
 * If this element cannot have annotations, the first annotative parent is searched.
 */
fun KtElement.isSuppressedBy(id: String)
		= this is KtAnnotated && this.isSuppressedBy(id) || findAnnotatedSuppressedParent(id)

private fun KtElement.findAnnotatedSuppressedParent(id: String): Boolean {
	val parent = PsiTreeUtil.getParentOfType(this, KtAnnotated::class.java, true)

	var suppressed = false
	if (parent != null && parent !is KtFile) {
		suppressed = if (parent.isSuppressedBy(id)) {
			true
		} else {
			parent.findAnnotatedSuppressedParent(id)
		}
	}

	return suppressed
}

private val detektSuppresionPrefixRegex = Regex("(?i)detekt([.:])")
private const val QUOTES = "\""

/**
 * Checks if this kt element is suppressed by @Suppress or @SuppressWarnings annotations.
 */
fun KtAnnotated.isSuppressedBy(id: String): Boolean {
	val valid = listOf(id, "ALL", "all")
	return annotationEntries.find { it.typeReferenceName.let { it == "Suppress" || it == "SuppressWarnings" } }
			?.valueArguments
			?.map { it.getArgumentExpression()?.text }
			?.map { it?.replace(detektSuppresionPrefixRegex, "") }
			?.map { it?.replace(QUOTES, "") }
			?.find { it in valid } != null
}
