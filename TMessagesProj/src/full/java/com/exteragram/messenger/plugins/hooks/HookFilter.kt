/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.hooks

import com.exteragram.messenger.plugins.PluginsConstants
import com.exteragram.messenger.utils.AppUtils
import de.robv.android.xposed.XC_MethodHook
import org.mvel2.MVEL
import java.io.Serializable

class HookFilter(
    val filterType: String,
) {
    private val typeId = typeIdFor(filterType)

    private class CompiledCondition(val source: String, val expression: Serializable?)

    @Volatile
    private var compiledCondition: CompiledCondition? = null

    var argIndex: Int? = null

    var orFilters: ArrayList<HookFilter>? = null

    var mvelExpression: String? = null

    var instanceOf: Class<*>? = null

    var `object`: Any? = null

    fun execute(param: XC_MethodHook.MethodHookParam, isBefore: Boolean): Boolean {
        try {
            when (typeId) {
                TYPE_OR -> {
                    val filters = orFilters
                    if (filters != null) {
                        for (filter in filters) {
                            if (filter.execute(param, isBefore)) {
                                return true
                            }
                        }
                    }
                    return false
                }

                TYPE_CONDITION -> {
                    val vars = HashMap<String, Any?>(4)
                    vars["param"] = param
                    vars["result"] = if (isBefore) null else param.result
                    vars["object"] = `object`

                    val expression = mvelExpression ?: return false
                    val compiled = getCompiledExpression(expression) ?: return false
                    val result = MVEL.executeExpression(
                        compiled,
                        param.thisObject,
                        vars,
                        Boolean::class.java
                    )
                    return result ?: false
                }

                TYPE_ARGUMENT_EQUAL,
                TYPE_ARGUMENT_NOT_EQUAL,
                TYPE_ARGUMENT_IS_INSTANCE_OF,
                TYPE_ARGUMENT_IS_FALSE,
                TYPE_ARGUMENT_IS_NULL,
                TYPE_ARGUMENT_IS_TRUE,
                TYPE_ARGUMENT_NOT_NULL -> {
                    val index = argIndex
                    val args = param.args
                    if (index == null || index !in args.indices) {
                        return false
                    }

                    val value = args[index]
                    return when (typeId) {
                        TYPE_ARGUMENT_EQUAL -> valuesEqual(value, `object`)
                        TYPE_ARGUMENT_NOT_EQUAL -> !valuesEqual(value, `object`)
                        TYPE_ARGUMENT_IS_INSTANCE_OF -> instanceOf?.isInstance(value) == true
                        TYPE_ARGUMENT_IS_FALSE -> value is Boolean && !value
                        TYPE_ARGUMENT_IS_NULL -> value == null
                        TYPE_ARGUMENT_IS_TRUE -> value is Boolean && value
                        TYPE_ARGUMENT_NOT_NULL -> value != null
                        else -> false
                    }
                }

                TYPE_RESULT_EQUAL,
                TYPE_RESULT_NOT_EQUAL,
                TYPE_RESULT_IS_FALSE,
                TYPE_RESULT_IS_INSTANCE_OF,
                TYPE_RESULT_IS_NULL,
                TYPE_RESULT_IS_TRUE,
                TYPE_RESULT_NOT_NULL -> {
                    if (isBefore) {
                        return false
                    }
                    val result = param.result

                    return when (typeId) {
                        TYPE_RESULT_EQUAL -> valuesEqual(result, `object`)
                        TYPE_RESULT_NOT_EQUAL -> !valuesEqual(result, `object`)
                        TYPE_RESULT_IS_FALSE -> result is Boolean && !result
                        TYPE_RESULT_IS_TRUE -> result is Boolean && result
                        TYPE_RESULT_IS_INSTANCE_OF -> instanceOf?.isInstance(result) == true
                        TYPE_RESULT_IS_NULL -> result == null
                        TYPE_RESULT_NOT_NULL -> result != null
                        else -> false
                    }
                }
            }
        } catch (e: Exception) {
            AppUtils.log(e)
        }

        return false
    }

    private fun getCompiledExpression(expression: String): Serializable? {
        val current = compiledCondition
        if (current != null && current.source == expression) {
            return current.expression
        }

        val compiled = try {
            MVEL.compileExpression(expression)
        } catch (e: Exception) {
            AppUtils.log(e)
            null
        }
        compiledCondition = CompiledCondition(expression, compiled)
        return compiled
    }

    private fun valuesEqual(a: Any?, b: Any?): Boolean {
        if (a == b) return true
        if (a is Number && b is Number) {
            if (a is Double || a is Float || b is Double || b is Float) {
                return a.toDouble() == b.toDouble()
            }
            return a.toLong() == b.toLong()
        }
        return false
    }

    companion object {
        private const val TYPE_UNKNOWN = 0
        private const val TYPE_OR = 1
        private const val TYPE_CONDITION = 2
        private const val TYPE_ARGUMENT_EQUAL = 3
        private const val TYPE_ARGUMENT_NOT_EQUAL = 4
        private const val TYPE_ARGUMENT_IS_INSTANCE_OF = 5
        private const val TYPE_ARGUMENT_IS_FALSE = 6
        private const val TYPE_ARGUMENT_IS_NULL = 7
        private const val TYPE_ARGUMENT_IS_TRUE = 8
        private const val TYPE_ARGUMENT_NOT_NULL = 9
        private const val TYPE_RESULT_EQUAL = 10
        private const val TYPE_RESULT_NOT_EQUAL = 11
        private const val TYPE_RESULT_IS_FALSE = 12
        private const val TYPE_RESULT_IS_INSTANCE_OF = 13
        private const val TYPE_RESULT_IS_NULL = 14
        private const val TYPE_RESULT_IS_TRUE = 15
        private const val TYPE_RESULT_NOT_NULL = 16

        private fun typeIdFor(filterType: String): Int {
            return when (filterType) {
                PluginsConstants.HookFilterTypes.OR -> TYPE_OR
                PluginsConstants.HookFilterTypes.CONDITION -> TYPE_CONDITION
                PluginsConstants.HookFilterTypes.ARGUMENT_EQUAL -> TYPE_ARGUMENT_EQUAL
                PluginsConstants.HookFilterTypes.ARGUMENT_NOT_EQUAL -> TYPE_ARGUMENT_NOT_EQUAL
                PluginsConstants.HookFilterTypes.ARGUMENT_IS_INSTANCE_OF -> TYPE_ARGUMENT_IS_INSTANCE_OF
                PluginsConstants.HookFilterTypes.ARGUMENT_IS_FALSE -> TYPE_ARGUMENT_IS_FALSE
                PluginsConstants.HookFilterTypes.ARGUMENT_IS_NULL -> TYPE_ARGUMENT_IS_NULL
                PluginsConstants.HookFilterTypes.ARGUMENT_IS_TRUE -> TYPE_ARGUMENT_IS_TRUE
                PluginsConstants.HookFilterTypes.ARGUMENT_NOT_NULL -> TYPE_ARGUMENT_NOT_NULL
                PluginsConstants.HookFilterTypes.RESULT_EQUAL -> TYPE_RESULT_EQUAL
                PluginsConstants.HookFilterTypes.RESULT_NOT_EQUAL -> TYPE_RESULT_NOT_EQUAL
                PluginsConstants.HookFilterTypes.RESULT_IS_FALSE -> TYPE_RESULT_IS_FALSE
                PluginsConstants.HookFilterTypes.RESULT_IS_INSTANCE_OF -> TYPE_RESULT_IS_INSTANCE_OF
                PluginsConstants.HookFilterTypes.RESULT_IS_NULL -> TYPE_RESULT_IS_NULL
                PluginsConstants.HookFilterTypes.RESULT_IS_TRUE -> TYPE_RESULT_IS_TRUE
                PluginsConstants.HookFilterTypes.RESULT_NOT_NULL -> TYPE_RESULT_NOT_NULL
                else -> TYPE_UNKNOWN
            }
        }
    }
}