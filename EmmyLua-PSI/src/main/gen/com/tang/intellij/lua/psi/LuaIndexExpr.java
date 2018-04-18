// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.ITy;

public interface LuaIndexExpr extends LuaExpr, PsiNameIdentifierOwner, LuaClassMember {

  @NotNull
  List<LuaExpr> getExprList();

  @Nullable
  PsiElement getId();

  @Nullable
  PsiElement getNameIdentifier();

  @NotNull
  PsiElement setName(String name);

  @Nullable
  String getName();

  int getTextOffset();

  //WARNING: getPresentation(...) is skipped
  //matching getPresentation(LuaIndexExpr, ...)
  //methods are not found in LuaPsiImplUtilKt

  @Nullable
  LuaLiteralExpr getIdExpr();

  //WARNING: toString(...) is skipped
  //matching toString(LuaIndexExpr, ...)
  //methods are not found in LuaPsiImplUtilKt

  @NotNull
  ITy guessParentType(SearchContext context);

  @NotNull
  ITy guessValueType(SearchContext context);

  @Nullable
  PsiElement getDot();

  @Nullable
  PsiElement getColon();

  @Nullable
  PsiElement getLbrack();

}
