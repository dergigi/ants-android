// Generated from grammar/AntsQuery.g4 by ANTLR 4.13.2
package org.dergigi.ants.query.generated;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link AntsQueryParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface AntsQueryVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link AntsQueryParser#query}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitQuery(AntsQueryParser.QueryContext ctx);
	/**
	 * Visit a parse tree produced by {@link AntsQueryParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpression(AntsQueryParser.ExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link AntsQueryParser#conjunction}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConjunction(AntsQueryParser.ConjunctionContext ctx);
	/**
	 * Visit a parse tree produced by the {@code scopedField}
	 * labeled alternative in {@link AntsQueryParser#primary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitScopedField(AntsQueryParser.ScopedFieldContext ctx);
	/**
	 * Visit a parse tree produced by the {@code field}
	 * labeled alternative in {@link AntsQueryParser#primary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitField(AntsQueryParser.FieldContext ctx);
	/**
	 * Visit a parse tree produced by the {@code group}
	 * labeled alternative in {@link AntsQueryParser#primary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGroup(AntsQueryParser.GroupContext ctx);
	/**
	 * Visit a parse tree produced by the {@code phrase}
	 * labeled alternative in {@link AntsQueryParser#primary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPhrase(AntsQueryParser.PhraseContext ctx);
	/**
	 * Visit a parse tree produced by the {@code term}
	 * labeled alternative in {@link AntsQueryParser#primary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTerm(AntsQueryParser.TermContext ctx);
	/**
	 * Visit a parse tree produced by {@link AntsQueryParser#value}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitValue(AntsQueryParser.ValueContext ctx);
}