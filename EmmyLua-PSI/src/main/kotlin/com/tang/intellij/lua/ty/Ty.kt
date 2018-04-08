/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tang.intellij.lua.ty

import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.search.SearchContext

enum class TyKind {
    Unknown,
    Primitive,
    Array,
    Function,
    Class,
    Union,
    Generic,
    Nil,
    Void,
    Tuple,
    GenericParam,
}
enum class TyPrimitiveKind {
    String,
    Number,
    Boolean,
    Table,
    Function
}
class TyFlags {
    companion object {
        const val ANONYMOUS = 0x1
        const val GLOBAL = 0x2
        const val SELF_FUNCTION = 0x4 // xxx.method()
    }
}

interface ITy : Comparable<ITy> {
    val kind: TyKind

    val displayName: String

    val flags: Int

    fun union(ty: ITy): ITy

    fun createTypeString(): String

    fun subTypeOf(other: ITy, context: SearchContext): Boolean

    fun getSuperClass(context: SearchContext): ITy?

    fun substitute(substitutor: ITySubstitutor): ITy

    fun eachTopClass(fn: Processor<ITyClass>)
}

fun ITy.hasFlag(flag: Int): Boolean = flags and flag == flag

val ITy.isGlobal: Boolean
    get() = hasFlag(TyFlags.GLOBAL)

val ITy.isAnonymous: Boolean
    get() = hasFlag(TyFlags.ANONYMOUS)

private val ITy.worth: Float get() {
    var value = 10f
    when(this) {
        is ITyClass -> {
            value = when {
                this is TyTable -> 9f
                this.isAnonymous -> 2f
                this.isGlobal -> 5f
                else -> 90f
            }
        }
        is ITyArray, is ITyGeneric -> value = 80f
        is TyPrimitive -> value = 70f
        is ITyFunction -> value = 60f
    }
    return value
}

abstract class Ty(override val kind: TyKind) : ITy {

    final override var flags: Int = 0

    fun addFlag(flag: Int) {
        flags = flags or flag
    }

    override fun union(ty: ITy): ITy {
        return TyUnion.union(this, ty)
    }

    override fun createTypeString(): String {
        val s = toString()
        return if (s.isEmpty()) Constants.WORD_ANY else s
    }

    override fun toString(): String {
        val list = mutableListOf<String>()
        TyUnion.each(this) { //尽量不使用Global
            if (!it.isAnonymous && !(it is ITyClass && it.isGlobal))
                list.add(it.displayName)
        }
        if (list.isEmpty()) { //使用Global
            TyUnion.each(this) {
                if (!it.isAnonymous && (it is ITyClass && it.isGlobal))
                    list.add(it.displayName)
            }
        }
        return list.joinToString("|")
    }

    override fun subTypeOf(other: ITy, context: SearchContext): Boolean {
        // Everything is subset of any
        if (other.kind == TyKind.Unknown) return true

        // Handle unions, subtype if subtype of any of the union components.
        if (other is TyUnion) return other.getChildTypes().any({ type -> subTypeOf(type, context) })

        // Classes are equal
        return this == other
    }

    override fun getSuperClass(context: SearchContext): ITy? {
        return null
    }

    override fun compareTo(other: ITy): Int {
        return other.worth.compareTo(worth)
    }

    override fun substitute(substitutor: ITySubstitutor): ITy {
        return substitutor.substitute(this)
    }

    override fun eachTopClass(fn: Processor<ITyClass>) {
        when (this) {
            is ITyClass -> fn.process(this)
            is TyUnion -> {
                ContainerUtil.process(getChildTypes()) {
                    if (it is ITyClass && !fn.process(it))
                        return@process false
                    true
                }
            }
            is TyTuple -> {
                list.firstOrNull()?.eachTopClass(fn)
            }
        }
    }

    companion object {

        val UNKNOWN = TyUnknown()
        val VOID = TyVoid()
        val BOOLEAN = TyPrimitive(TyPrimitiveKind.Boolean, "boolean")
        val STRING = TyPrimitive(TyPrimitiveKind.String, "string")
        val NUMBER = TyPrimitive(TyPrimitiveKind.Number, "number")
        val TABLE = TyPrimitive(TyPrimitiveKind.Table, "table")
        val FUNCTION = TyPrimitive(TyPrimitiveKind.Function, "function")
        val NIL = TyNil()

        private fun getPrimitive(mark: Byte): Ty {
            return when (mark.toInt()) {
                TyPrimitiveKind.Boolean.ordinal -> BOOLEAN
                TyPrimitiveKind.String.ordinal -> STRING
                TyPrimitiveKind.Number.ordinal -> NUMBER
                TyPrimitiveKind.Table.ordinal -> TABLE
                TyPrimitiveKind.Function.ordinal -> FUNCTION
                else -> UNKNOWN
            }
        }

        private fun getKind(ordinal: Int): TyKind {
            return TyKind.values().firstOrNull { ordinal == it.ordinal } ?: TyKind.Unknown
        }

        fun getBuiltin(name: String): ITy? {
            return when (name) {
                Constants.WORD_NIL -> Ty.NIL
                Constants.WORD_VOID -> Ty.VOID
                Constants.WORD_ANY -> Ty.UNKNOWN
                Constants.WORD_BOOLEAN -> Ty.BOOLEAN
                Constants.WORD_STRING -> Ty.STRING
                Constants.WORD_NUMBER -> Ty.NUMBER
                Constants.WORD_TABLE -> Ty.TABLE
                Constants.WORD_FUNCTION -> Ty.FUNCTION
                else -> null
            }
        }

        fun isInvalid(ty: ITy?): Boolean {
            return ty == null || ty is TyUnknown || ty is TyNil || ty is TyVoid
        }
    }
}

class TyPrimitive(val primitiveKind: TyPrimitiveKind, override val displayName: String) : Ty(TyKind.Primitive) {
    override fun equals(other: Any?): Boolean {
        return other is TyPrimitive && other.primitiveKind == primitiveKind
    }

    override fun hashCode(): Int {
        return primitiveKind.hashCode()
    }
}

interface ITyArray : ITy {
    val base: ITy
}

class TyArray(override val base: ITy) : Ty(TyKind.Array), ITyArray {
    override val displayName: String
        get() = "${base.displayName}[]"

    override fun equals(other: Any?): Boolean {
        return other is ITyArray && base == other.base
    }

    override fun hashCode(): Int {
        return displayName.hashCode()
    }

    override fun subTypeOf(other: ITy, context: SearchContext): Boolean {
        return super.subTypeOf(other, context) || (other is TyArray && base.subTypeOf(other.base, context)) || other == Ty.TABLE
    }

    override fun substitute(substitutor: ITySubstitutor): ITy {
        return TyArray(base.substitute(substitutor))
    }
}

class TyUnion : Ty(TyKind.Union) {
    private val childSet = mutableSetOf<ITy>()
    fun getChildTypes() = childSet

    override val displayName: String get() {
        val list = mutableListOf<String>()
        eachPerfect(this) {
            list.add(it.displayName)
            true
        }
        return list.joinToString("|")
    }

    val size:Int
        get() = childSet.size

    private fun union2(ty: ITy): TyUnion {
        if (ty is TyUnion) {
            ty.childSet.forEach { addChild(it) }
        }
        else addChild(ty)
        return this
    }

    private fun addChild(ty: ITy): Boolean {
        return childSet.add(ty)
    }

    override fun subTypeOf(other: ITy, context: SearchContext): Boolean {
        return super.subTypeOf(other, context) || childSet.any { type -> type.subTypeOf(other, context) }
    }

    override fun substitute(substitutor: ITySubstitutor): ITy {
        val u = TyUnion()
        childSet.forEach { u.childSet.add(it.substitute(substitutor)) }
        return u
    }

    companion object {
        fun <T : ITy> find(ty: ITy, clazz: Class<T>): T? {
            if (clazz.isInstance(ty))
                return clazz.cast(ty)
            var ret: T? = null
            process(ty) {
                if (clazz.isInstance(it)) {
                    ret = clazz.cast(it)
                    return@process false
                }
                true
            }
            return ret
        }

        fun process(ty: ITy, process: (ITy) -> Boolean) {
            if (ty is TyUnion) {
                // why nullable ???
                val arr: Array<ITy?> = ty.childSet.toTypedArray()
                for (child in arr) {
                    if (child != null && !process(child))
                        break
                }
            } else process(ty)
        }

        fun each(ty: ITy, fn: (ITy) -> Unit) {
            process(ty) {
                fn(it)
                true
            }
        }

        fun eachPerfect(ty: ITy, process: (ITy) -> Boolean) {
            if (ty is TyUnion) {
                val list = ty.childSet.sorted()
                for (iTy in list) {
                    if (!process(iTy))
                        break
                }
            } else process(ty)
        }

        fun union(t1: ITy, t2: ITy): ITy {
            return when {
                isInvalid(t1) -> t2
                isInvalid(t2) -> t1
                t1 is TyUnion -> t1.union2(t2)
                t2 is TyUnion -> t2.union2(t1)
                else -> {
                    val u = TyUnion()
                    u.addChild(t1)
                    u.addChild(t2)
                    //if t1 == t2
                    if (u.childSet.size == 1) t1 else u
                }
            }
        }

        fun getPerfectClass(ty: ITy): ITyClass? {
            var tc: ITyClass? = null
            var anonymous: ITyClass? = null
            var global: ITyClass? = null
            process(ty) {
                if (it is ITyClass) {
                    if (it.isAnonymous)
                        anonymous = it
                    else if (it.isGlobal)
                        global = it
                    else {
                        tc = it
                        return@process false
                    }
                }
                true
            }
            return tc ?: global ?: anonymous
        }
    }
}

class TyUnknown : Ty(TyKind.Unknown) {
    override val displayName: String
        get() = Constants.WORD_ANY

    override fun equals(other: Any?): Boolean {
        return other is TyUnknown
    }

    override fun hashCode(): Int {
        return Constants.WORD_ANY.hashCode()
    }

    override fun subTypeOf(other: ITy, context: SearchContext): Boolean {
        return true
    }
}

class TyNil : Ty(TyKind.Nil) {
    override val displayName: String
        get() = Constants.WORD_NIL

    override fun subTypeOf(other: ITy, context: SearchContext): Boolean {
        //return super.subTypeOf(other, context) || other is TyNil || !LuaSettings.instance.isNilStrict
        TODO()
    }
}

class TyVoid : Ty(TyKind.Void) {
    override val displayName: String
        get() = Constants.WORD_VOID

    override fun subTypeOf(other: ITy, context: SearchContext): Boolean {
        return false
    }
}

class TyTuple(val list: List<ITy>) : Ty(TyKind.Tuple) {
    override val displayName: String
        get() = "(${list.joinToString(", ")})"

    val size: Int get() {
        return list.size
    }

    override fun substitute(substitutor: ITySubstitutor): ITy {
        val list = list.map { it.substitute(substitutor) }
        return TyTuple(list)
    }
}