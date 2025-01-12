/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hive.hplsql;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Stack;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;

public class Select {

  Exec exec = null;
  Stack<Var> stack = null;
  Conf conf;
  
  boolean trace = false; 
  
  Select(Exec e) {
    exec = e;  
    stack = exec.getStack();
    conf = exec.getConf();
    trace = exec.getTrace();
  }
   
  /**
   * Executing or building SELECT statement
   */
  public Integer select(HplsqlParser.Select_stmtContext ctx) { 
    if (ctx.parent instanceof HplsqlParser.StmtContext) {
      exec.stmtConnList.clear();
      trace(ctx, "SELECT");
    }
    boolean oldBuildSql = exec.buildSql; 
    exec.buildSql = true;
    StringBuilder sql = new StringBuilder();
    if (ctx.cte_select_stmt() != null) {
      sql.append(evalPop(ctx.cte_select_stmt()).toString());
      sql.append("\n");
    }
    sql.append(evalPop(ctx.fullselect_stmt()).toString());
    exec.buildSql = oldBuildSql;
    if (!(ctx.parent instanceof HplsqlParser.StmtContext)) {           // No need to execute at this stage
      exec.stackPush(sql);
      return 0;  
    }    
    if (trace && ctx.parent instanceof HplsqlParser.StmtContext) {
      trace(ctx, sql.toString());
    }
    if (exec.getOffline()) {
      trace(ctx, "Not executed - offline mode set");
      return 0;
    }
    String conn = exec.getStatementConnection();
    Query query = exec.executeQuery(ctx, sql.toString(), conn);
    if (query.error()) { 
      exec.signal(query);
      return 1;
    }
    trace(ctx, "SELECT completed successfully");
    exec.setSqlSuccess();
    try {
      ResultSet rs = query.getResultSet();
      ResultSetMetaData rm = null;
      if (rs != null) {
        rm = rs.getMetaData();
      }
      HplsqlParser.Into_clauseContext into = getIntoClause(ctx);
      if (into != null) {
        trace(ctx, "SELECT INTO statement executed");
        int cols = into.ident().size();          
        if (rs.next()) {
          for (int i = 1; i <= cols; i++) {
            Var var = exec.findVariable(into.ident(i-1).getText());
            if (var != null) {
              var.setValue(rs, rm, i);
              if (trace) {
                trace(ctx, "COLUMN: " + rm.getColumnName(i) + ", " + rm.getColumnTypeName(i));
                trace(ctx, "SET " + var.getName() + " = " + var.toString());
              }            
            } 
            else if(trace) {
              trace(ctx, "Variable not found: " + into.ident(i-1).getText());
            }
          }
          exec.incRowCount();
          exec.setSqlSuccess();
        }
        else {
          exec.setSqlCode(100);
          exec.signal(Signal.Type.NOTFOUND);
        }
      }
      // Print all results for standalone SELECT statement
      else if (ctx.parent instanceof HplsqlParser.StmtContext) {
        int cols = rm.getColumnCount();
        if (trace) {
          trace(ctx, "Standalone SELECT executed: " + cols + " columns in the result set");
        }
        while (rs.next()) {
          for (int i = 1; i <= cols; i++) {
            if (i > 1) {
              System.out.print("\t");
            }
            System.out.print(rs.getString(i));
          }
          System.out.println("");
          exec.incRowCount();
        }
      }
      // Scalar subquery
      else {
        trace(ctx, "Scalar subquery executed, first row and first column fetched only");
        if(rs.next()) {
          exec.stackPush(new Var().setValue(rs, rm, 1));
          exec.setSqlSuccess();          
        }
        else {
          evalNull();
          exec.setSqlCode(100);
        }
      }
    }
    catch (SQLException e) {
      exec.signal(query);
      exec.closeQuery(query, exec.conf.defaultConnection);
      return 1;
    }
    exec.closeQuery(query, exec.conf.defaultConnection);
    return 0; 
  }  

  /**
   * Common table expression (WITH clause)
   */
  public Integer cte(HplsqlParser.Cte_select_stmtContext ctx) {
    int cnt = ctx.cte_select_stmt_item().size();
    StringBuilder sql = new StringBuilder();
    sql.append("WITH ");
    for (int i = 0; i < cnt; i++) {
      HplsqlParser.Cte_select_stmt_itemContext c = ctx.cte_select_stmt_item(i);      
      sql.append(c.ident().getText());
      if (c.cte_select_cols() != null) {
        sql.append(" " + exec.getFormattedText(c.cte_select_cols()));
      }
      sql.append(" AS (");
      sql.append(evalPop(ctx.cte_select_stmt_item(i).fullselect_stmt()).toString());
      sql.append(")");
      if (i + 1 != cnt) {
        sql.append(",\n");
      }
    }
    exec.stackPush(sql);
    return 0;
  }
  
  /**
   * Part of SELECT
   */
  public Integer fullselect(HplsqlParser.Fullselect_stmtContext ctx) { 
    int cnt = ctx.fullselect_stmt_item().size();
    StringBuilder sql = new StringBuilder();
    for (int i = 0; i < cnt; i++) {
      String part = evalPop(ctx.fullselect_stmt_item(i)).toString(); 
      sql.append(part);
      if (i + 1 != cnt) {
        sql.append("\n" + getText(ctx.fullselect_set_clause(i)) + "\n");
      }
    }
    exec.stackPush(sql);
    return 0; 
  }
  
  public Integer subselect(HplsqlParser.Subselect_stmtContext ctx) {
    StringBuilder sql = new StringBuilder();
    if (ctx.T_SELECT() != null) {
      sql.append(ctx.T_SELECT().getText());
    }
    sql.append(" " + evalPop(ctx.select_list()));
    if (ctx.from_clause() != null) {
      sql.append(" " + evalPop(ctx.from_clause()));
    } else {
      sql.append(" FROM " + conf.dualTable);
    }
    if (ctx.where_clause() != null) {
      sql.append(" " + evalPop(ctx.where_clause()));
    }
    if (ctx.group_by_clause() != null) {
      sql.append(" " + getText(ctx.group_by_clause()));
    }
    if (ctx.having_clause() != null) {
      sql.append(" " + getText(ctx.having_clause()));
    }
    if (ctx.order_by_clause() != null) {
      sql.append(" " + getText(ctx.order_by_clause()));
    }
    if (ctx.select_options() != null) {
      sql.append(" " + evalPop(ctx.select_options()));
    }
    if (ctx.select_list().select_list_limit() != null) {
      sql.append(" LIMIT " + evalPop(ctx.select_list().select_list_limit().expr()));
    }
    exec.stackPush(sql);
    return 0; 
  }
  
  /**
   * SELECT list 
   */
  public Integer selectList(HplsqlParser.Select_listContext ctx) { 
    StringBuilder sql = new StringBuilder();
    if (ctx.select_list_set() != null) {
      sql.append(exec.getText(ctx.select_list_set())).append(" ");
    }
    int cnt = ctx.select_list_item().size();
    for (int i = 0; i < cnt; i++) {
      if (ctx.select_list_item(i).select_list_asterisk() == null) {
        sql.append(evalPop(ctx.select_list_item(i)));
        if (ctx.select_list_item(i).select_list_alias() != null) {
          sql.append(" " + exec.getText(ctx.select_list_item(i).select_list_alias()));
        }
      }
      else {
        sql.append(exec.getText(ctx.select_list_item(i).select_list_asterisk()));
      }
      if (i + 1 < cnt) {
        sql.append(", ");
      }
    }
    exec.stackPush(sql);
    return 0;
  }

  /**
   * FROM clause
   */
  public Integer from(HplsqlParser.From_clauseContext ctx) { 
    StringBuilder sql = new StringBuilder();
    sql.append(ctx.T_FROM().getText()).append(" ");
    sql.append(evalPop(ctx.from_table_clause()));
    int cnt = ctx.from_join_clause().size();
    for (int i = 0; i < cnt; i++) {
      sql.append(evalPop(ctx.from_join_clause(i)));
    }
    exec.stackPush(sql);
    return 0;
  }
  
  /**
   * Single table name in FROM
   */
  public Integer fromTable(HplsqlParser.From_table_name_clauseContext ctx) {     
    StringBuilder sql = new StringBuilder();
    sql.append(evalPop(ctx.table_name()));
    if (ctx.from_alias_clause() != null) {
      sql.append(" ").append(exec.getText(ctx.from_alias_clause()));
    }
    exec.stackPush(sql);
    return 0; 
  }
 
  /**
   * JOIN clause in FROM
   */
  public Integer fromJoin(HplsqlParser.From_join_clauseContext ctx) {
    StringBuilder sql = new StringBuilder();
    if (ctx.T_COMMA() != null) {
      sql.append(", ");
      sql.append(evalPop(ctx.from_table_clause()));
    }
    else if (ctx.from_join_type_clause() != null) {
      sql.append(" ");
      sql.append(exec.getText(ctx.from_join_type_clause()));
      sql.append(" ");
      sql.append(evalPop(ctx.from_table_clause()));
      sql.append(" ");
      sql.append(exec.getText(ctx, ctx.T_ON().getSymbol(), ctx.bool_expr().getStop()));
    }
    exec.stackPush(sql);
    return 0; 
  }

  /**
   * FROM TABLE (VALUES ...) clause
   */
  public Integer fromTableValues(HplsqlParser.From_table_values_clauseContext ctx) {
    StringBuilder sql = new StringBuilder();
    int rows = ctx.from_table_values_row().size();
    sql.append("(");
    for (int i = 0; i < rows; i++) {
      int cols = ctx.from_table_values_row(i).expr().size();
      int cols_as = ctx.from_alias_clause().L_ID().size();
      sql.append("SELECT ");
      for (int j = 0; j < cols; j++) {
        sql.append(evalPop(ctx.from_table_values_row(i).expr(j)));
        if (j < cols_as) {
          sql.append(" AS ");
          sql.append(ctx.from_alias_clause().L_ID(j));  
        }
        if (j + 1 < cols) {
          sql.append(", ");
        }
      }
      sql.append(" FROM " + conf.dualTable);
      if (i + 1 < rows) {
        sql.append("\nUNION ALL\n");
      }
    }
    sql.append(") ");
    if (ctx.from_alias_clause() != null) {
      sql.append(ctx.from_alias_clause().ident().getText());
    }
    exec.stackPush(sql);
    return 0; 
  }
  
  /**
   * WHERE clause
   */
  public Integer where(HplsqlParser.Where_clauseContext ctx) { 
    StringBuilder sql = new StringBuilder();
    sql.append(ctx.T_WHERE().getText());
    sql.append(" " + evalPop(ctx.bool_expr()));
    exec.stackPush(sql);
    return 0;
  }
  
  /**
   * Get INTO clause
   */
  HplsqlParser.Into_clauseContext getIntoClause(HplsqlParser.Select_stmtContext ctx) {
    if (ctx.fullselect_stmt().fullselect_stmt_item(0).subselect_stmt() != null) {
      return ctx.fullselect_stmt().fullselect_stmt_item(0).subselect_stmt().into_clause();
    }
    return null;
  }
  
  /** 
   * SELECT statement options - LIMIT n, WITH UR i.e
   */
  public Integer option(HplsqlParser.Select_options_itemContext ctx) { 
    if (ctx.T_LIMIT() != null) {
      exec.stackPush("LIMIT " + evalPop(ctx.expr()));
    }
    return 0; 
  }
  
  /**
   * Evaluate the expression to NULL
   */
  void evalNull() {
    exec.stackPush(Var.Null); 
  }

  /**
   * Evaluate the expression and pop value from the stack
   */
  Var evalPop(ParserRuleContext ctx) {
    exec.visit(ctx);
    if (!exec.stack.isEmpty()) { 
      return exec.stackPop();
    }
    return Var.Empty;
  }

  /**
   * Get node text including spaces
   */
  String getText(ParserRuleContext ctx) {
    return ctx.start.getInputStream().getText(new Interval(ctx.start.getStartIndex(), ctx.stop.getStopIndex()));
  }
  
  /**
   * Execute rules
   */
  Integer visit(ParserRuleContext ctx) {
    return exec.visit(ctx);  
  } 
  
  /**
   * Execute children rules
   */
  Integer visitChildren(ParserRuleContext ctx) {
    return exec.visitChildren(ctx);  
  }  
  
  /**
   * Trace information
   */
  void trace(ParserRuleContext ctx, String message) {
    exec.trace(ctx, message);
  }
}