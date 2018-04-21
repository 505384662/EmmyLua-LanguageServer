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

@file:Suppress("UNUSED_PARAMETER")

package com.tang.intellij.lua.psi

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.comment.LuaCommentUtil
import com.tang.intellij.lua.comment.psi.api.LuaComment
import com.tang.intellij.lua.ext.recursionGuard
import com.tang.intellij.lua.lang.type.LuaString
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.*
import java.util.*

fun setName(owner: PsiNameIdentifierOwner, name: String): PsiElement {
    /*val oldId = owner.nameIdentifier
    if (oldId != null) {
        val newId = LuaElementFactory.createIdentifier(owner.project, name)
        oldId.replace(newId)
        return newId
    }
    return owner*/
    TODO()
}

fun getNameIdentifier(nameDef: LuaNameDef): PsiElement {
    return nameDef.firstChild
}

/**
 * LuaNameDef 只可能在本文件中搜
 * @param nameDef def
 * *
 * @return SearchScope
 */
fun getUseScope(nameDef: LuaNameDef): SearchScope {
    //return GlobalSearchScope.fileScope(nameDef.containingFile)
    TODO()
}

fun getReferences(element: LuaPsiElement): Array<PsiReference> {
    return ReferenceProvidersRegistry.getReferencesFromProviders(element)
}

/**
 * 寻找 Comment
 * @param declaration owner
 * *
 * @return LuaComment
 */
fun getComment(declaration: LuaCommentOwner): LuaComment? {
    return LuaCommentUtil.findComment(declaration)
}

fun getNameIdentifier(classMethodDef: LuaClassMethodDef): PsiElement? {
    return classMethodDef.classMethodName.id
}

fun getName(classMethodDef: LuaClassMethodDef): String? {
    /*val stub = classMethodDef.stub
    if (stub != null)
        return stub.name*/
    return getName(classMethodDef as PsiNameIdentifierOwner)
}

fun isStatic(classMethodDef: LuaClassMethodDef): Boolean {
    /*val stub = classMethodDef.stub
    if (stub != null)
        return stub.isStatic*/

    return classMethodDef.classMethodName.dot != null
}

//private val GET_CLASS_METHOD = Key.create<ParameterizedCachedValue<ITy, SearchContext>>("GET_CLASS_METHOD")

/**
 * 寻找对应的类
 * @param classMethodDef def
 * *
 * @return LuaType
 */
fun guessParentType(classMethodDef: LuaClassMethodDef, context: SearchContext): ITy {
    var type: ITy = Ty.UNKNOWN
    val expr = classMethodDef.classMethodName.expr
    val ty = expr.guessType(context)
    val perfect = TyUnion.getPerfectClass(ty)
    if (perfect is ITyClass)
        type = perfect
    return type
}

fun getNameIdentifier(funcDef: LuaFuncDef): PsiElement? {
    return funcDef.id
}

fun getName(funcDef: LuaFuncDef): String? {
    /*val stub = funcDef.stub
    if (stub != null)
        return stub.name*/
    return getName(funcDef as PsiNameIdentifierOwner)
}

fun guessParentType(funcDef: LuaFuncDef, searchContext: SearchContext): ITyClass {
    return TyClass.G
}

/**
 * 猜出前面的类型
 * @param callExpr call expr
 * *
 * @return LuaType
 */
fun guessParentType(callExpr: LuaCallExpr, context: SearchContext): ITy {
    return callExpr.expr.guessType(context)
}

/**
 * 找出函数体
 * @param callExpr call expr
 * *
 * @return LuaFuncBodyOwner
 */
fun resolveFuncBodyOwner(callExpr: LuaCallExpr, context: SearchContext): LuaFuncBodyOwner? {
    return recursionGuard(callExpr, Computable {
        var owner: LuaFuncBodyOwner? = null
        val expr = callExpr.expr
        if (expr is LuaIndexExpr) {
            val resolve = resolve(expr, context)
            if (resolve is LuaFuncBodyOwner)
                owner = resolve
        } else if (expr is LuaNameExpr) {
            owner = resolveFuncBodyOwner(expr, context)
        }
        owner
    })
}

/**
 * 获取第一个字符串参数
 * @param callExpr callExpr
 * *
 * @return String PsiElement
 */
fun getFirstStringArg(callExpr: LuaCallExpr): PsiElement? {
    val args = callExpr.args
    var path: PsiElement? = null

    when (args) {
        is LuaSingleArg -> {
            val expr = args.expr
            if (expr is LuaLiteralExpr) path = expr
        }
        is LuaListArgs -> args.exprList.let { list ->
            if (list.isNotEmpty() && list[0] is LuaLiteralExpr) {
                val valueExpr = list[0] as LuaLiteralExpr
                if (valueExpr.kind == LuaLiteralKind.String)
                    path = valueExpr
            }
        }
    }
    return path
}

fun isStaticMethodCall(callExpr: LuaCallExpr): Boolean {
    val expr = callExpr.expr
    if (expr is LuaNameExpr)
        return true
    return expr is LuaIndexExpr && expr.colon == null
}

fun isMethodCall(callExpr: LuaCallExpr): Boolean {
    val expr = callExpr.expr
    return expr is LuaIndexExpr && expr.colon != null
}

fun isFunctionCall(callExpr: LuaCallExpr): Boolean {
    return callExpr.expr is LuaNameExpr
}

fun guessTypeAt(list: LuaExprList, context: SearchContext): ITy {
    val exprList = list.exprStubList

    val expr = exprList.getOrNull(context.index) ?: exprList.lastOrNull()
    if (expr != null) {
        //local function getValues12() return 1, 2 end
        //local function getValues34() return 3, 4 end
        //local a, b = getValues12() -- a = 1, b = 2
        //local a, b, c = getValues12(), 3, 4 --a = 1, b = 3, c =  4
        //local a, b, c = getValues12(), getValue34() --a = 1, b = 3, c =  4
        if (exprList.size > 1) {
            val nameSize = context.index + 1
            if (nameSize > exprList.size) {
                val valueSize = exprList.size
                context.index = nameSize - valueSize
            } else context.index = 0
        }
        return expr.guessType(context)
    }
    return Ty.UNKNOWN
}

fun guessParentType(indexExpr: LuaIndexExpr, context: SearchContext): ITy {
    val expr = PsiTreeUtil.getChildOfType(indexExpr, LuaExpr::class.java)
    return expr?.guessType(context) ?: Ty.UNKNOWN
}

fun getNameIdentifier(indexExpr: LuaIndexExpr): PsiElement? {
    val id = indexExpr.id
    if (id != null)
        return id
    return indexExpr.idExpr
}

/**
 * xx['id']
 */
fun getIdExpr(indexExpr: LuaIndexExpr): LuaLiteralExpr? {
    val bracket = indexExpr.lbrack
    if (bracket != null) {
        val nextLeaf = PsiTreeUtil.getNextSiblingOfType(bracket, LuaExpr::class.java)
        if (nextLeaf is LuaLiteralExpr && nextLeaf.kind == LuaLiteralKind.String)
            return nextLeaf
    }
    return null
}

fun getName(indexExpr: LuaIndexExpr): String? {
    /*val stub = indexExpr.stub
    if (stub != null)
        return stub.name*/

    // var.name
    val id = indexExpr.id
    if (id != null)
        return id.text

    // var['name']
    val idExpr = indexExpr.idExpr
    if (idExpr != null)
        return LuaString.getContent(idExpr.text).value

    return null
}

fun setName(indexExpr: LuaIndexExpr, name: String): PsiElement {
    /*if (indexExpr.id != null)
        return setName(indexExpr as PsiNameIdentifierOwner, name)
    val idExpr = indexExpr.idExpr
    if (idExpr != null) {
        val text = idExpr.text
        val content = LuaString.getContent(text)
        val newText = text.substring(0, content.start) + name + text.substring(content.end)
        val newId = LuaElementFactory.createLiteral(indexExpr.project, newText)
        return idExpr.replace(newId)
    }
    return indexExpr*/
    TODO()
}

fun guessValueType(indexExpr: LuaIndexExpr, context: SearchContext): ITy {
    var ret: ITy = Ty.UNKNOWN
    val assignStat = indexExpr.assignStat
    if (assignStat != null) {
        context.index = assignStat.getIndexFor(indexExpr)
        ret = assignStat.valueExprList?.guessTypeAt(context) ?: Ty.UNKNOWN
    }
    return ret
}

fun findField(table: LuaTableExpr, fieldName: String): LuaTableField? {
    return table.tableFieldList.firstOrNull { fieldName == it.name }
}

fun getTableFieldList(table: LuaTableExpr): List<LuaTableField> {
    //return PsiTreeUtil.getStubChildrenOfTypeAsList(table, LuaTableField::class.java)
    TODO()
}

fun getParamNameDefList(funcBodyOwner: LuaFuncBodyOwner): List<LuaParamNameDef> {
    val funcBody = funcBodyOwner.funcBody
    return funcBody?.paramNameDefList ?: emptyList()
}

fun getParamNameDefList(forAStat: LuaForAStat): List<LuaParamNameDef> {
    val list = ArrayList<LuaParamNameDef>()
    list.add(forAStat.paramNameDef)
    return list
}

fun guessReturnType(owner: LuaFuncBodyOwner, searchContext: SearchContext): ITy {
    return inferReturnTy(owner, searchContext)
}

fun getParams(owner: LuaFuncBodyOwner): Array<LuaParamInfo> {
    /*if (owner is StubBasedPsiElementBase<*>) {
        val stub = owner.stub
        if (stub is LuaFuncBodyOwnerStub<*>) {
            return stub.params
        }
    }*/
    return getParamsInner(owner)
}

private fun getParamsInner(funcBodyOwner: LuaFuncBodyOwner): Array<LuaParamInfo> {
    var comment: LuaComment? = null
    if (funcBodyOwner is LuaCommentOwner) {
        comment = LuaCommentUtil.findComment(funcBodyOwner)
    }

    val paramNameList = funcBodyOwner.paramNameDefList
    if (paramNameList != null) {
        val list = mutableListOf<LuaParamInfo>()
        for (i in paramNameList.indices) {
            val paramInfo = LuaParamInfo()
            val paramName = paramNameList[i].text
            paramInfo.name = paramName
            // param types
            if (comment != null) {
                val paramDef = comment.getParamDef(paramName)
                if (paramDef != null) {
                    paramInfo.ty = paramDef.type
                }
            }
            list.add(paramInfo)
        }
        //check varArgs
        funcBodyOwner.funcBody?.ellipsis?.let {
            val args = LuaParamInfo()
            args.name = "..."
            args.isVarArgs = true
            list.add(args)
        }
        return list.toTypedArray()
    }
    return emptyArray()
}

fun getParamSignature(funcBodyOwner: LuaFuncBodyOwner): String {
    val params = funcBodyOwner.params
    val list = arrayOfNulls<String>(params.size)
    for (i in params.indices) {
        val lpi = params[i]
        list[i] = lpi.name
    }
    return "(" + list.joinToString(", ") + ")"
}

fun getName(localFuncDef: LuaLocalFuncDef): String? {
    /*val stub = localFuncDef.stub
    if (stub != null)
        return stub.name*/
    return getName(localFuncDef as PsiNameIdentifierOwner)
}

fun getNameIdentifier(localFuncDef: LuaLocalFuncDef): PsiElement? {
    return localFuncDef.id
}

fun getUseScope(localFuncDef: LuaLocalFuncDef): SearchScope {
    //return GlobalSearchScope.fileScope(localFuncDef.containingFile)
    TODO()
}

fun getName(nameDef: LuaNameDef): String {
    /*val stub = nameDef.stub
    if (stub != null)
        return stub.name*/
    return nameDef.id.text
}

fun getName(identifierOwner: PsiNameIdentifierOwner): String? {
    val id = identifierOwner.nameIdentifier
    return id?.text
}

fun getTextOffset(localFuncDef: PsiNameIdentifierOwner): Int {
    val id = localFuncDef.nameIdentifier
    if (id != null) return id.textOffset
    return localFuncDef.node.startOffset
}

fun getNameIdentifier(tableField: LuaTableField): PsiElement? {
    val id = tableField.id
    if (id != null)
        return id
    return tableField.idExpr
}

fun guessParentType(tableField: LuaTableField, context: SearchContext): ITy {
    val tbl = PsiTreeUtil.getParentOfType(tableField, LuaTableExpr::class.java)!!
    return tbl.guessType(context)
}

/*fun guessType(tableField: LuaTableField, context: SearchContext): ITy {
    val stub = tableField.stub
    //from comment
    val docTy = if (stub != null) stub.docTy else tableField.comment?.docTy
    if (docTy != null)
        return docTy

    //guess from value
    val valueExpr = PsiTreeUtil.getStubChildOfType(tableField, LuaExpr::class.java)
    if (valueExpr != null) {
        return valueExpr.guessType(context)
    }
    return Ty.UNKNOWN
}*/

fun getName(tableField: LuaTableField): String? {
    /*val stub = tableField.stub
    if (stub != null)
        return stub.name*/
    val id = tableField.id
    if (id != null)
        return id.text

    val idExpr = tableField.idExpr
    if (idExpr is LuaLiteralExpr && idExpr.kind == LuaLiteralKind.String)
        return LuaString.getContent(idExpr.text).value
    return null
}

fun getFieldName(tableField: LuaTableField): String? {
    return getName(tableField)
}

/**
 * xx['id']
 */
fun getIdExpr(tableField: LuaTableField): LuaExpr? {
    val bracket = tableField.lbrack
    if (bracket != null) {
        return PsiTreeUtil.getNextSiblingOfType(bracket, LuaExpr::class.java)
    }
    return null
}

fun getNameIdentifier(ref: LuaNameExpr): PsiElement {
    return ref.id
}

fun getName(nameExpr: LuaNameExpr): String {
    /*val stub = nameExpr.stub
    if (stub != null)
        return stub.name*/
    return nameExpr.text
}

fun guessReturnType(returnStat: LuaReturnStat?, index: Int, context: SearchContext): ITy {
    if (returnStat != null) {
        val returnExpr = returnStat.exprList
        if (returnExpr != null) {
            context.index = index
            return if (context.guessTuple())
                returnExpr.guessType(context)
            else
                returnExpr.guessTypeAt(context)
        }
    }
    return Ty.UNKNOWN
}

fun getNameIdentifier(label: LuaLabelStat): PsiElement? {
    return label.id
}

fun getVisibility(member: LuaClassMember): Visibility {
    /*if (member is StubBasedPsiElement<*>) {
        val stub = member.stub
        if (stub is LuaClassMemberStub) {
            return stub.visibility
        }
    }
    if (member is LuaCommentOwner) {
        val comment = member.comment
        comment?.findTag(LuaDocAccessModifier::class.java)?.let {
            return Visibility.get(it.text)
        }
    }*/
    return Visibility.PUBLIC
}

fun getVisibility(classMethodDef: LuaClassMethodDef): Visibility {
    /*val stub = classMethodDef.stub
    if (stub != null) {

    }*/
    return getVisibility(classMethodDef as LuaClassMember)
}

fun getExpr(exprStat: LuaExprStat): LuaExpr {
    //return PsiTreeUtil.getStubChildOfType(exprStat, LuaExpr::class.java)!!
    TODO()
}

fun getPresentation(psi: PsiElement): ItemPresentation {
    TODO()
}

fun toString(psi: PsiElement): String {
    TODO()
}