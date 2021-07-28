/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.dialect;

import java.util.List;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.ComparisonOperator;
import org.hibernate.sql.ast.SqlAstNodeRenderingMode;
import org.hibernate.sql.ast.spi.AbstractSqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.ast.tree.cte.CteStatement;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.Literal;
import org.hibernate.sql.ast.tree.expression.SqlTuple;
import org.hibernate.sql.ast.tree.expression.Summarization;
import org.hibernate.sql.ast.tree.select.QueryPart;
import org.hibernate.sql.ast.tree.select.QuerySpec;
import org.hibernate.sql.exec.spi.JdbcOperation;

/**
 * A SQL AST translator for HSQL.
 *
 * @author Christian Beikov
 */
public class HSQLSqlAstTranslator<T extends JdbcOperation> extends AbstractSqlAstTranslator<T> {

	public HSQLSqlAstTranslator(SessionFactoryImplementor sessionFactory, Statement statement) {
		super( sessionFactory, statement );
	}

	@Override
	public boolean supportsFilterClause() {
		return true;
	}

	@Override
	protected LockStrategy determineLockingStrategy(
			QuerySpec querySpec,
			ForUpdateClause forUpdateClause,
			Boolean followOnLocking) {
		if ( getDialect().getVersion() < 200 ) {
			return LockStrategy.NONE;
		}
		return super.determineLockingStrategy( querySpec, forUpdateClause, followOnLocking );
	}

	@Override
	protected void renderForUpdateClause(QuerySpec querySpec, ForUpdateClause forUpdateClause) {
		if ( getDialect().getVersion() < 200 ) {
			return;
		}
		super.renderForUpdateClause( querySpec, forUpdateClause );
	}

	@Override
	public void visitOffsetFetchClause(QueryPart queryPart) {
		if ( supportsOffsetFetchClause() ) {
			assertRowsOnlyFetchClauseType( queryPart );
			renderOffsetFetchClause( queryPart, true );
		}
		else {
			renderLimitOffsetClause( queryPart );
		}
	}

	@Override
	protected void renderSearchClause(CteStatement cte) {
		// HSQL does not support this, but it's just a hint anyway
	}

	@Override
	protected void renderCycleClause(CteStatement cte) {
		// HSQL does not support this, but it can be emulated
	}

	@Override
	protected void renderSelectExpression(Expression expression) {
		renderSelectExpressionWithCastedOrInlinedPlainParameters( expression );
	}

	@Override
	protected void renderSelectTupleComparison(
			List<SqlSelection> lhsExpressions,
			SqlTuple tuple,
			ComparisonOperator operator) {
		emulateTupleComparison( lhsExpressions, tuple.getExpressions(), operator, true );
	}

	protected void renderComparison(Expression lhs, ComparisonOperator operator, Expression rhs) {
		switch ( operator ) {
			case DISTINCT_FROM:
			case NOT_DISTINCT_FROM:
				// HSQL does not like parameters in the distinct from predicate
				render( lhs, SqlAstNodeRenderingMode.NO_PLAIN_PARAMETER );
				appendSql( " " );
				appendSql( operator.sqlText() );
				appendSql( " " );
				render( rhs, SqlAstNodeRenderingMode.NO_PLAIN_PARAMETER );
				break;
			default:
				renderComparisonStandard( lhs, operator, rhs );
				break;
		}
	}

	@Override
	protected void renderPartitionItem(Expression expression) {
		if ( expression instanceof Literal ) {
			appendSql( "'0' || '0'" );
		}
		else if ( expression instanceof Summarization ) {
			// This could theoretically be emulated by rendering all grouping variations of the query and
			// connect them via union all but that's probably pretty inefficient and would have to happen
			// on the query spec level
			throw new UnsupportedOperationException( "Summarization is not supported by DBMS!" );
		}
		else {
			expression.accept( this );
		}
	}

	@Override
	protected boolean supportsRowValueConstructorSyntax() {
		return false;
	}

	@Override
	protected boolean supportsRowValueConstructorSyntaxInInList() {
		return false;
	}

	@Override
	protected boolean supportsRowValueConstructorSyntaxInQuantifiedPredicates() {
		return false;
	}

	@Override
	protected String getFromDual() {
		return " from (values(0))";
	}

	@Override
	protected String getFromDualForSelectOnly() {
		return getFromDual();
	}

	private boolean supportsOffsetFetchClause() {
		return getDialect().getVersion() >= 250;
	}
}