package com.tang.intellij.lua.stubs.index

import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil
import com.tang.intellij.lua.comment.psi.LuaDocFieldDef
import com.tang.intellij.lua.psi.LuaClassMember
import com.tang.intellij.lua.psi.LuaClassMethod
import com.tang.intellij.lua.psi.LuaTableField
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.IndexSink
import com.tang.intellij.lua.stubs.StubKeys
import com.tang.intellij.lua.ty.ITyClass

class LuaClassMemberIndex : StubIndex<Int, LuaClassMember>() {
    override fun getKey() = StubKeys.CLASS_MEMBER

    companion object {
        val instance = LuaClassMemberIndex()

        fun process(key: String, context: SearchContext, processor: Processor<LuaClassMember>): Boolean {
            if (context.isDumb)
                return false
            val all = LuaClassMemberIndex.instance.get(key.hashCode(), context.project, context.getScope())
            return ContainerUtil.process(all, processor)
        }

        fun process(className: String, fieldName: String, context: SearchContext, processor: Processor<LuaClassMember>, deep: Boolean = true): Boolean {
            val key = "$className*$fieldName"
            if (!process(key, context, processor))
                return false

            if (deep) {
                val classDef = LuaClassIndex.find(className, context)
                if (classDef != null) {
                    val type = classDef.type
                    // from alias
                    type.lazyInit(context)
                    val notFound = type.processAlias(Processor {
                        process(it, fieldName, context, processor, false)
                    })
                    if (!notFound)
                        return false

                    // from supper
                    val superClassName = type.superClassName
                    if (superClassName != null && superClassName != className) {
                        return process(superClassName, fieldName, context, processor)
                    }
                }
            }
            return true
        }

        fun find(type: ITyClass, fieldName: String, context: SearchContext): LuaClassMember? {
            var perfect: LuaClassMember? = null
            var docField: LuaDocFieldDef? = null
            var tableField: LuaTableField? = null
            processAll(type, fieldName, context, Processor {
                when (it) {
                    is LuaDocFieldDef -> {
                        docField = it
                        false
                    }
                    is LuaTableField -> {
                        tableField = it
                        true
                    }
                    else -> {
                        if (perfect == null)
                            perfect = it
                        true
                    }
                }
            })
            if (docField != null) return docField
            if (tableField != null) return tableField
            return perfect
        }

        fun processAll(type: ITyClass, fieldName: String, context: SearchContext, processor: Processor<LuaClassMember>) {
            process(type.className, fieldName, context, processor)
        }

        fun processAll(type: ITyClass, context: SearchContext, processor: Processor<LuaClassMember>) {
            if (process(type.className, context, processor)) {
                type.lazyInit(context)
                type.processAlias(Processor {
                    process(it, context, processor)
                })
            }
        }

        fun findMethod(className: String, memberName: String, context: SearchContext, deep: Boolean = true): LuaClassMethod? {
            var target: LuaClassMethod? = null
            process(className, memberName, context, Processor {
                if (it is LuaClassMethod) {
                    target = it
                    return@Processor false
                }
                true
            }, deep)
            return target
        }

        /*fun indexStub(indexSink: IndexSink, className: String, memberName: String) {
            indexSink.occurrence(StubKeys.CLASS_MEMBER, className.hashCode())
            indexSink.occurrence(StubKeys.CLASS_MEMBER, "$className*$memberName".hashCode())
        }*/
    }
}