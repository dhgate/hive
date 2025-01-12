/**
   Licensed to the Apache Software Foundation (ASF) under one or more 
   contributor license agreements.  See the NOTICE file distributed with 
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with 
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

// HPL/SQL Procedural SQL Extension Grammar 
grammar Hplsql;

program : block ;

block : (begin_end_block | stmt)+ ;                      // Multiple consecutive blocks/statements

begin_end_block :
       declare_block? T_BEGIN block exception_block? T_END
     ;
     
single_block_stmt :                                      // Single BEGIN END block (but nested blocks are possible) or single statement
       T_BEGIN block T_END  
     | stmt T_SEMICOLON?
     ;

stmt : 
       assignment_stmt
     | break_stmt
     | call_stmt
     | close_stmt
     | copy_from_local_stmt
     | copy_stmt
     | commit_stmt
     | create_function_stmt
     | create_index_stmt
     | create_local_temp_table_stmt
     | create_procedure_stmt
     | create_table_stmt
     | declare_stmt
     | delete_stmt
     | drop_stmt
     | exec_stmt 
     | exit_stmt
     | fetch_stmt
     | for_cursor_stmt
     | for_range_stmt
     | if_stmt     
     | include_stmt
     | insert_stmt
     | get_diag_stmt
     | grant_stmt
     | leave_stmt
     | map_object_stmt
     | merge_stmt
     | open_stmt
     | print_stmt
     | resignal_stmt
     | return_stmt
     | rollback_stmt
     | select_stmt
     | signal_stmt
     | update_stmt
     | use_stmt
     | values_into_stmt
     | while_stmt
     | label    
     | hive     
     | host
     | expr_stmt     
     | semicolon_stmt      // Placed here to allow null statements ;;...          
     ;
     
semicolon_stmt :
       T_SEMICOLON
     | '@' | '#' | '/'
     ;

exception_block :       // Exception block
       T_EXCEPTION exception_block_item+
     ;

exception_block_item : 
       T_WHEN L_ID T_THEN block ~(T_WHEN | T_END)       
     ;
     
expr_stmt :             // Standalone expression
       expr
     ;

assignment_stmt :       // Assignment statement
       T_SET? assignment_stmt_item (T_COMMA assignment_stmt_item)*  
     ;

assignment_stmt_item : 
       assignment_stmt_single_item
     | assignment_stmt_multiple_item
     | assignment_stmt_select_item
     ;

assignment_stmt_single_item : 
       ident T_COLON? T_EQUAL expr
     ;

assignment_stmt_multiple_item : 
       T_OPEN_P ident (T_COMMA ident)* T_CLOSE_P T_COLON? T_EQUAL T_OPEN_P expr (T_COMMA expr)* T_CLOSE_P
     ;

assignment_stmt_select_item : 
       (ident | (T_OPEN_P ident (T_COMMA ident)* T_CLOSE_P)) T_COLON? T_EQUAL T_OPEN_P select_stmt T_CLOSE_P
     ;
     
break_stmt :
       T_BREAK
     ;
     
call_stmt :
       T_CALL ident expr_func_params?
     ;
     
declare_stmt :          // Declaration statement
       T_DECLARE declare_stmt_item (T_COMMA declare_stmt_item)*
     ;

declare_block :         // Declaration block
       T_DECLARE declare_stmt_item T_SEMICOLON (declare_stmt_item T_SEMICOLON)*
     ;
   
     
declare_stmt_item :
       declare_var_item 
     | declare_condition_item  
     | declare_cursor_item
     | declare_handler_item
     | declare_temporary_table_item
     ;

declare_var_item :
       ident (T_COMMA ident)* T_AS? dtype dtype_len? dtype_attr* dtype_default? 
     ;

declare_condition_item :    // Condition declaration 
       ident T_CONDITION
     ;
     
declare_cursor_item :      // Cursor declaration 
       (T_CURSOR ident | ident T_CURSOR) declare_cursor_return? (T_IS | T_AS | T_FOR) (select_stmt | expr )
     ;
     
declare_cursor_return :
       T_WITHOUT T_RETURN
     | T_WITH T_RETURN T_ONLY? (T_TO (T_CALLER | T_CLIENT))?
     ;

declare_handler_item :     // Condition handler declaration 
       (T_CONTINUE | T_EXIT) T_HANDLER T_FOR (T_SQLEXCEPTION | T_SQLWARNING | T_NOT T_FOUND | ident) single_block_stmt
     ;
     
declare_temporary_table_item :     // DECLARE TEMPORARY TABLE statement
       T_GLOBAL? T_TEMPORARY T_TABLE ident T_OPEN_P create_table_columns T_CLOSE_P create_table_options?
     ;
     
create_table_stmt :
       T_CREATE T_TABLE (T_IF T_NOT T_EXISTS)? ident T_OPEN_P create_table_columns T_CLOSE_P create_table_options?
     ;
     
create_local_temp_table_stmt :
       T_CREATE (T_LOCAL T_TEMPORARY | (T_SET | T_MULTISET)? T_VOLATILE) T_TABLE ident T_OPEN_P create_table_columns T_CLOSE_P create_table_options?
     ;
     
create_table_columns :         
       create_table_columns_item (T_COMMA create_table_columns_item)*
     ;
       
create_table_columns_item :
       ident dtype dtype_len? dtype_attr* create_table_column_inline_cons? 
     | T_CONSTRAINT ident create_table_column_cons
     ;

create_table_column_inline_cons :
       dtype_default
     | T_NOT? T_NULL
     | T_PRIMARY T_KEY
     ;
     
create_table_column_cons :
       T_PRIMARY T_KEY T_OPEN_P ident (T_COMMA ident)*  T_CLOSE_P
     ;

create_table_options :
       create_table_options_item+        
     ;
     
create_table_options_item :
       T_ON T_COMMIT (T_DELETE | T_PRESERVE) T_ROWS 
     | create_table_options_db2_item  
     | create_table_options_hive_item  
     ;

create_table_options_db2_item :
       T_IN ident
     | T_WITH T_REPLACE
     | T_DISTRIBUTE T_BY T_HASH T_OPEN_P ident (T_COMMA ident)* T_CLOSE_P
     | T_LOGGED 
     | T_NOT T_LOGGED
     ;
     
create_table_options_hive_item :
       create_table_hive_row_format
     ;
     
create_table_hive_row_format :
       T_ROW T_FORMAT T_DELIMITED create_table_hive_row_format_fields*
     ;
     
create_table_hive_row_format_fields :
       T_FIELDS T_TERMINATED T_BY expr (T_ESCAPED T_BY expr)?
     | T_COLLECTION T_ITEMS T_TERMINATED T_BY expr
     | T_MAP T_KEYS T_TERMINATED T_BY expr
     | T_LINES T_TERMINATED T_BY expr
     | T_NULL T_DEFINED T_AS expr
     ;
     
dtype :                  // Data types
       T_CHAR
     | T_BIGINT
     | T_DATE
     | T_DEC
     | T_DECIMAL
     | T_FLOAT
     | T_INT
     | T_INTEGER
     | T_NUMBER
     | T_SMALLINT
     | T_STRING
     | T_TIMESTAMP
     | T_VARCHAR
     | T_VARCHAR2
     | L_ID             // User-defined data type
     ;
     
dtype_len :             // Data type length or size specification
       T_OPEN_P L_INT (T_COMMA L_INT)? T_CLOSE_P
     ;
     
dtype_attr :
       T_CHARACTER T_SET ident
     | T_NOT? (T_CASESPECIFIC | T_CS)
     ;

dtype_default :         // Default clause in variable declaration
       T_COLON? T_EQUAL expr
     | T_DEFAULT expr
     ;
     
create_function_stmt : 
      (T_ALTER | T_CREATE (T_OR T_REPLACE)? | T_REPLACE) T_FUNCTION ident create_routine_params create_function_return (T_AS | T_IS)? single_block_stmt 
    ;
     
create_function_return :
       (T_RETURN | T_RETURNS) dtype dtype_len?
     ;

create_procedure_stmt : 
      (T_ALTER | T_CREATE (T_OR T_REPLACE)? | T_REPLACE) (T_PROCEDURE | T_PROC) ident create_routine_params create_routine_options? (T_AS | T_IS)? label? single_block_stmt (ident T_SEMICOLON)? 
    ;

create_routine_params :
       T_OPEN_P (create_routine_param_item (T_COMMA create_routine_param_item)*)? T_CLOSE_P
     ;
     
create_routine_param_item :
       (T_IN | T_OUT | T_INOUT | T_IN T_OUT)? ident dtype dtype_len? dtype_attr* dtype_default? 
     | ident (T_IN | T_OUT | T_INOUT | T_IN T_OUT)? dtype dtype_len? dtype_attr* dtype_default? 
     ;
     
create_routine_options :
       create_routine_option+
     ;
create_routine_option :
       T_LANGUAGE T_SQL       
     | T_SQL T_SECURITY (T_CREATOR | T_DEFINER | T_INVOKER | T_OWNER)
     | T_DYNAMIC T_RESULT T_SETS L_INT
     ;
     
drop_stmt :             // DROP statement
       T_DROP T_TABLE (T_IF T_EXISTS)? table_name
     ;

exec_stmt :             // EXEC, EXECUTE IMMEDIATE statement 
       (T_EXEC | T_EXECUTE) T_IMMEDIATE? expr (T_INTO L_ID (T_COMMA L_ID)*)? using_clause? 
     ;

if_stmt :               // IF statement 
       if_plsql_stmt
     | if_tsql_stmt 
     ;

if_plsql_stmt : 
       T_IF bool_expr T_THEN block elseif_block* else_block? T_END T_IF 
     ;

if_tsql_stmt : 
       T_IF bool_expr single_block_stmt (T_ELSE single_block_stmt)?  
     ;
     
elseif_block :
       (T_ELSIF | T_ELSEIF) bool_expr T_THEN block
     ;

else_block :
       T_ELSE block
     ;
     
include_stmt :          // INCLUDE statement
       T_INCLUDE file_name
     ;  
     
insert_stmt :           // INSERT statement
       T_INSERT (T_OVERWRITE T_TABLE | T_INTO T_TABLE?) table_name insert_stmt_cols? (select_stmt | insert_stmt_rows)
     ;
     
insert_stmt_cols :
       T_OPEN_P ident (T_COMMA ident)* T_CLOSE_P 
     ;
     
insert_stmt_rows :
       T_VALUES insert_stmt_row (T_COMMA insert_stmt_row)*
     ;

insert_stmt_row:
       T_OPEN_P expr (T_COMMA expr)* T_CLOSE_P
     ;
     
exit_stmt :
       T_EXIT L_ID? (T_WHEN bool_expr)?
     ;
     
get_diag_stmt :         // GET DIAGNOSTICS statement
       T_GET T_DIAGNOSTICS get_diag_stmt_item
     ;
     
get_diag_stmt_item :
       get_diag_stmt_exception_item
     | get_diag_stmt_rowcount_item
     ;
     
get_diag_stmt_exception_item :
       T_EXCEPTION L_INT ident T_EQUAL T_MESSAGE_TEXT
     ;

get_diag_stmt_rowcount_item :
       ident T_EQUAL T_ROW_COUNT
     ;
     
grant_stmt :            
       T_GRANT grant_stmt_item (T_COMMA grant_stmt_item)* T_TO ident
     ;
     
grant_stmt_item :
       T_EXECUTE T_ON T_PROCEDURE ident
     ;
     
leave_stmt :
       T_LEAVE L_ID?
     ;
     
map_object_stmt :
       T_MAP T_OBJECT expr (T_TO expr)? (T_AT expr)?
     ;
     
open_stmt :             // OPEN cursor statement
       T_OPEN L_ID (T_FOR (expr | select_stmt))?
     ;

fetch_stmt :            // FETCH cursor statement
       T_FETCH T_FROM? L_ID T_INTO L_ID (T_COMMA L_ID)*
     ;
     
close_stmt :            // CLOSE cursor statement
       T_CLOSE L_ID
     ;
     
copy_from_local_stmt :  // COPY FROM LOCAL statement
       T_COPY T_FROM T_LOCAL copy_source (T_COMMA copy_source)* T_TO copy_target copy_file_option*
     ;
     
copy_stmt :             // COPY statement
       T_COPY (table_name | T_OPEN_P select_stmt T_CLOSE_P) T_TO copy_target copy_option*
     ;
     
copy_source :
       (ident | expr | L_FILE)
     ;

copy_target :
       (ident | expr | L_FILE)
     ;
    
copy_option :
       T_AT ident
     | T_BATCHSIZE expr
     | T_DELIMITER expr
     | T_SQLINSERT ident
     ;

copy_file_option :
       T_DELETE
     | T_IGNORE
     | T_OVERWRITE
     ;
     
commit_stmt :           // COMMIT statement
       T_COMMIT T_WORK?
     ;
     
create_index_stmt :     // CREATE INDEX statement
       T_CREATE T_UNIQUE? T_INDEX ident T_ON table_name T_OPEN_P create_index_col (T_COMMA create_index_col)* T_CLOSE_P
     ;
     
create_index_col : 
       ident (T_ASC | T_DESC)?
     ;
     
print_stmt :            // PRINT statement 
       T_PRINT expr
     | T_PRINT T_OPEN_P expr T_CLOSE_P
     ;
     
resignal_stmt :         // RESIGNAL statement
       T_RESIGNAL (T_SQLSTATE T_VALUE? expr (T_SET T_MESSAGE_TEXT T_EQUAL expr)? )?
     ;
     
return_stmt :           // RETURN statement
       T_RETURN expr?
     ;
     
rollback_stmt :         // ROLLBACK statement
       T_ROLLBACK T_WORK?
     ;
     
signal_stmt :          // SIGNAL statement
       T_SIGNAL ident
     ;

use_stmt :              // USE statement
       T_USE expr
     ;
     
values_into_stmt :     // VALUES INTO statement
       T_VALUES T_OPEN_P? expr (T_COMMA expr)* T_CLOSE_P? T_INTO T_OPEN_P? ident (T_COMMA ident)* T_CLOSE_P? 
     ;

while_stmt :            // WHILE loop statement
       T_WHILE bool_expr (T_DO | T_LOOP | T_THEN | T_BEGIN) block T_END (T_WHILE | T_LOOP)? 
     ;

for_cursor_stmt :       // FOR (cursor) statement
       T_FOR L_ID T_IN T_OPEN_P? select_stmt T_CLOSE_P? T_LOOP block T_END T_LOOP
     ;
     
for_range_stmt :        // FOR (Integer range) statement
       T_FOR L_ID T_IN T_REVERSE? expr T_DOT2 expr ((T_BY | T_STEP) expr)? T_LOOP block T_END T_LOOP
     ;
     
label :
       L_LABEL
     | T_LESS T_LESS L_ID T_GREATER T_GREATER
     ;

using_clause :          // USING var,... clause
       T_USING expr (T_COMMA expr)*
     ;

select_stmt :            // SELECT statement
       cte_select_stmt? fullselect_stmt       
     ;
     
cte_select_stmt :
       T_WITH cte_select_stmt_item (T_COMMA cte_select_stmt_item)*
     ;
     
cte_select_stmt_item :
       ident cte_select_cols? T_AS T_OPEN_P fullselect_stmt T_CLOSE_P
     ;
     
cte_select_cols :
       T_OPEN_P ident (T_COMMA ident)* T_CLOSE_P
     ;
     
fullselect_stmt : 
       fullselect_stmt_item (fullselect_set_clause fullselect_stmt_item)* 
     ;

fullselect_stmt_item : 
       subselect_stmt
     | T_OPEN_P fullselect_stmt T_CLOSE_P
     ;

fullselect_set_clause :
       T_UNION T_ALL?
     | T_EXCEPT T_ALL?
     | T_INTERSECT T_ALL?     
     ;
  
subselect_stmt : 
       (T_SELECT | T_SEL) select_list into_clause? from_clause? where_clause? group_by_clause? having_clause? order_by_clause? select_options?
     ;

select_list :           
       select_list_set? select_list_limit? select_list_item (T_COMMA select_list_item)*
     ;

select_list_set :
       T_ALL 
     | T_DISTINCT
     ;
       
select_list_limit :       
       T_TOP expr
     ;

select_list_item :
       (expr select_list_alias? | select_list_asterisk)  
     ;
     
select_list_alias :
       T_AS? L_ID
     | T_OPEN_P T_TITLE L_S_STRING T_CLOSE_P
     ;
     
select_list_asterisk :
       (L_ID '.')? '*' 
     ;
     
into_clause :
       T_INTO ident (T_COMMA ident)*
     ;
     
from_clause :           
       T_FROM from_table_clause (from_join_clause)*
     ;
     
from_table_clause :
       from_table_name_clause
     | from_subselect_clause
     | from_table_values_clause
     ;
     
from_table_name_clause :
       table_name from_alias_clause?
     ;     

from_subselect_clause :
       T_OPEN_P subselect_stmt T_CLOSE_P from_alias_clause?
     ;
     
from_join_clause :
       T_COMMA from_table_clause
     | from_join_type_clause from_table_clause T_ON bool_expr
     ;
     
from_join_type_clause :
       T_INNER T_JOIN
     | (T_LEFT | T_RIGHT | T_FULL) T_OUTER? T_JOIN
     ;
     
from_table_values_clause:
       T_TABLE T_OPEN_P T_VALUES from_table_values_row (T_COMMA from_table_values_row)* T_CLOSE_P from_alias_clause?
     ;
     
from_table_values_row:
       expr
     | T_OPEN_P expr (T_COMMA expr)* T_CLOSE_P 
     ;

from_alias_clause :
       {!_input.LT(1).getText().equalsIgnoreCase("GROUP") &&
        !_input.LT(1).getText().equalsIgnoreCase("ORDER") &&
        !_input.LT(1).getText().equalsIgnoreCase("LIMIT")}?
       T_AS? ident (T_OPEN_P L_ID (T_COMMA L_ID)* T_CLOSE_P)? 
     ;
     
table_name :
       ident
     ;
     
where_clause :           
       T_WHERE bool_expr
     ;
 
group_by_clause :
       T_GROUP T_BY expr (T_COMMA expr)*
     ;
     
having_clause :           
       T_HAVING bool_expr
     ;     

order_by_clause :
       T_ORDER T_BY expr (T_ASC | T_DESC)? (T_COMMA expr (T_ASC | T_DESC)?)*
     ;
     
select_options :
       select_options_item+
     ;

select_options_item :
       T_LIMIT expr
     | T_WITH (T_RR | T_RS | T_CS | T_UR)
     ;

update_stmt :                              // UPDATE statement
       T_UPDATE update_table T_SET assignment_stmt_item (T_COMMA assignment_stmt_item)* where_clause? update_upsert?
     ;

update_table :
       (table_name | (T_OPEN_P select_stmt T_CLOSE_P)) (T_AS? ident)?
     ;     
     
update_upsert :
       T_ELSE insert_stmt
     ;
     
merge_stmt :                              // MERGE statement
       T_MERGE T_INTO merge_table T_USING merge_table T_ON bool_expr merge_condition+
     ;
     
merge_table :
       (table_name | (T_OPEN_P select_stmt T_CLOSE_P)) (T_AS? ident)?
     ; 
     
merge_condition :
       T_WHEN T_NOT? T_MATCHED (T_AND bool_expr)? T_THEN merge_action
     | T_ELSE T_IGNORE
     ;
     
merge_action :
       T_INSERT insert_stmt_cols? T_VALUES insert_stmt_row 
     | T_UPDATE T_SET assignment_stmt_item (T_COMMA assignment_stmt_item)* 
     | T_DELETE
     ;
     
delete_stmt :                             // DELETE statement
       T_DELETE T_FROM? table_name (T_AS? ident)? where_clause?
     ;
     
bool_expr :                               // Boolean condition
       T_OPEN_P bool_expr T_CLOSE_P 
     | bool_expr bool_expr_logical_operator bool_expr 
     | bool_expr_atom
     ;

bool_expr_atom :
      bool_expr_unary
    | bool_expr_binary
    ;
    
bool_expr_unary :
      expr T_IS T_NOT? T_NULL
    | expr T_BETWEEN expr T_AND expr
    | bool_expr_single_in
    | bool_expr_multi_in
    ;
    
bool_expr_single_in :
      expr T_NOT? T_IN T_OPEN_P ((expr (T_COMMA expr)*) | select_stmt) T_CLOSE_P 
    ;

bool_expr_multi_in :
      T_OPEN_P expr (T_COMMA expr)* T_CLOSE_P T_NOT? T_IN T_OPEN_P select_stmt T_CLOSE_P 
    ;
    
bool_expr_binary :
       expr bool_expr_binary_operator expr
     ;
     
bool_expr_logical_operator :
       T_AND 
     | T_OR
     ;      

bool_expr_binary_operator :
       T_EQUAL 
     | T_EQUAL2 
     | T_NOTEQUAL 
     | T_NOTEQUAL2 
     | T_LESS 
     | T_LESSEQUAL 
     | T_GREATER 
     | T_GREATEREQUAL 
     | T_NOT? (T_LIKE | T_RLIKE | T_REGEXP)
     ;

expr : 
       expr interval_item
     | expr T_MUL expr 
     | expr T_DIV expr  
     | expr T_ADD expr  
     | expr T_SUB expr   
     | T_OPEN_P expr T_CLOSE_P 
     | expr_concat
     | expr_case
     | expr_agg_window_func
     | expr_spec_func
     | expr_func                          
     | expr_atom    
     ;


expr_atom : 
       date_literal
     | timestamp_literal
     | ident 
     | string
     | dec_number
     | interval_number
     | int_number
     | null_const
     ;
     
interval_item :
       T_DAY 
     | T_DAYS
     | T_MICROSECOND 
     | T_MICROSECONDS  
     ;
     
interval_number :
       int_number interval_item 
     ;
     
expr_concat :                  // String concatenation operator
       expr_concat_item (T_PIPE | T_CONCAT) expr_concat_item ((T_PIPE | T_CONCAT) expr_concat_item)*
     ;
     
expr_concat_item : 
       T_OPEN_P expr T_CLOSE_P 
     | expr_case
     | expr_agg_window_func
     | expr_spec_func
     | expr_func                          
     | expr_atom 
     ;

expr_case :                    // CASE expression
       expr_case_simple
     | expr_case_searched
     ;

expr_case_simple :              
       T_CASE expr (T_WHEN expr T_THEN expr)+ (T_ELSE expr)? T_END
     ;

expr_case_searched :              
       T_CASE (T_WHEN bool_expr T_THEN expr)+ (T_ELSE expr)? T_END
     ;
     
expr_agg_window_func :
       T_AVG T_OPEN_P expr_func_all_distinct? expr T_CLOSE_P expr_func_over_clause?
     | T_COUNT T_OPEN_P ((expr_func_all_distinct? expr) | '*') T_CLOSE_P expr_func_over_clause?
     | T_COUNT_BIG T_OPEN_P ((expr_func_all_distinct? expr) | '*') T_CLOSE_P expr_func_over_clause?
     | T_DENSE_RANK T_OPEN_P T_CLOSE_P expr_func_over_clause
     | T_FIRST_VALUE T_OPEN_P expr T_CLOSE_P expr_func_over_clause
     | T_LAG T_OPEN_P expr (T_COMMA expr (T_COMMA expr)?)? T_CLOSE_P expr_func_over_clause
     | T_LAST_VALUE T_OPEN_P expr T_CLOSE_P expr_func_over_clause
     | T_LEAD T_OPEN_P expr (T_COMMA expr (T_COMMA expr)?)? T_CLOSE_P expr_func_over_clause
     | T_MAX T_OPEN_P expr_func_all_distinct? expr T_CLOSE_P expr_func_over_clause?
     | T_MIN T_OPEN_P expr_func_all_distinct? expr T_CLOSE_P expr_func_over_clause?
     | T_RANK T_OPEN_P T_CLOSE_P expr_func_over_clause
     | T_ROW_NUMBER T_OPEN_P T_CLOSE_P expr_func_over_clause
     | T_STDEV T_OPEN_P expr_func_all_distinct? expr T_CLOSE_P expr_func_over_clause?   
     | T_SUM T_OPEN_P expr_func_all_distinct? expr T_CLOSE_P expr_func_over_clause?
     | T_VAR T_OPEN_P expr_func_all_distinct? expr T_CLOSE_P expr_func_over_clause?
     | T_VARIANCE T_OPEN_P expr_func_all_distinct? expr T_CLOSE_P expr_func_over_clause?
     ; 

expr_func_all_distinct :
       T_ALL 
     | T_DISTINCT 
     ; 

expr_func_over_clause :
       T_OVER T_OPEN_P expr_func_partition_by_clause? order_by_clause? T_CLOSE_P
     ; 

expr_func_partition_by_clause :
       T_PARTITION T_BY ident (T_COMMA ident)*
     ; 
     
expr_spec_func : 
       T_ACTIVITY_COUNT
     | T_CAST T_OPEN_P expr T_AS  dtype dtype_len? T_CLOSE_P
     | T_COUNT T_OPEN_P (expr | '*') T_CLOSE_P
     | T_CURRENT_DATE | T_CURRENT T_DATE
     | (T_CURRENT_TIMESTAMP | T_CURRENT T_TIMESTAMP) (T_OPEN_P expr T_CLOSE_P)?
     | T_CURRENT_USER | T_CURRENT T_USER
     | T_MAX_PART_STRING T_OPEN_P expr (T_COMMA expr (T_COMMA expr T_EQUAL expr)*)? T_CLOSE_P 
     | T_MIN_PART_STRING T_OPEN_P expr (T_COMMA expr (T_COMMA expr T_EQUAL expr)*)? T_CLOSE_P 
     | T_MAX_PART_INT T_OPEN_P expr (T_COMMA expr (T_COMMA expr T_EQUAL expr)*)? T_CLOSE_P 
     | T_MIN_PART_INT T_OPEN_P expr (T_COMMA expr (T_COMMA expr T_EQUAL expr)*)? T_CLOSE_P 
     | T_MAX_PART_DATE T_OPEN_P expr (T_COMMA expr (T_COMMA expr T_EQUAL expr)*)? T_CLOSE_P 
     | T_MIN_PART_DATE T_OPEN_P expr (T_COMMA expr (T_COMMA expr T_EQUAL expr)*)? T_CLOSE_P 
     | T_PART_LOC T_OPEN_P expr (T_COMMA expr T_EQUAL expr)+ (T_COMMA expr)? T_CLOSE_P 
     | T_TRIM T_OPEN_P expr T_CLOSE_P
     | T_SUBSTRING T_OPEN_P expr T_FROM expr (T_FOR expr)? T_CLOSE_P
     | T_SYSDATE
     | T_USER
     ;
     
expr_func : 
       ident expr_func_params 
     ;

expr_func_params : 
       T_OPEN_P (expr (T_COMMA expr)*)? T_CLOSE_P 
     ;
     
hive :
       T_HIVE hive_item*
     ;

hive_item :
       P_e expr
     | P_f expr
     | P_hiveconf L_ID T_EQUAL expr 
     | P_i expr
     | P_S     
     | P_h
     ;  

host :     
       '!' host_cmd  ';'                   // OS command
     | host_stmt
     ;

host_cmd :     
       .*?          
     ;
     
host_stmt :     
       T_HOST expr          
     ;
     
file_name :
       L_ID | L_FILE
     ;
     
date_literal :                             // DATE 'YYYY-MM-DD' literal
       T_DATE string
     ;

timestamp_literal :                       // TIMESTAMP 'YYYY-MM-DD HH:MI:SS.FFF' literal
       T_TIMESTAMP string
     ;
     
ident :
       L_ID
     | non_reserved_words
     ;
     
string :                                   // String literal (single or double quoted)
       L_S_STRING                          # single_quotedString
     | L_D_STRING                          # double_quotedString
     ;

int_number :                               // Integer (positive or negative)
     ('-' | '+')? L_INT
     ;

dec_number :                               // Decimal number (positive or negative)
     ('-' | '+')? L_DEC
     ;
     
null_const :                              // NULL constant
       T_NULL
     ;
     
non_reserved_words :                      // Tokens that are not reserved words and can be used as identifiers
       T_ACTIVITY_COUNT
     | T_ALL 
     | T_ALTER
     | T_AND
     | T_AS     
     | T_ASC    
     | T_AT
     | T_AVG
     | T_BATCHSIZE
     | T_BEGIN   
     | T_BETWEEN
     | T_BIGINT  
     | T_BREAK   
     | T_BY    
     | T_CALL     
     | T_CALLER      
     | T_CASE   
     | T_CASESPECIFIC
     | T_CAST
     | T_CHAR  
     | T_CHARACTER  
     | T_CLIENT     
     | T_CLOSE 
     | T_COLLECTION     
     | T_COPY
     | T_COMMIT
     | T_CONCAT 
     | T_CONDITION
     | T_CONSTRAINT
     | T_CONTINUE
     | T_COUNT   
     | T_COUNT_BIG   
     | T_CREATE
     | T_CREATOR
     | T_CS
     | T_CURRENT 
     | T_CURRENT_DATE
     | T_CURRENT_TIMESTAMP
     | T_CURRENT_USER
     | T_CURSOR  
     | T_DATE     
     | T_DAY
     | T_DAYS
     | T_DEC      
     | T_DECIMAL  
     | T_DECLARE 
     | T_DEFAULT  
     | T_DEFINED
     | T_DEFINER
     | T_DELETE
     | T_DELIMITED
     | T_DELIMITER
     | T_DENSE_RANK
     | T_DESC     
     | T_DIAGNOSTICS
     | T_DISTINCT 
     | T_DISTRIBUTE
     | T_DO         
     | T_DROP    
     | T_DYNAMIC      
     // T_ELSE reserved word         
     // T_ELSEIF reserved word       
     // T_ELSIF reserved word        
     // T_END reserved word    
     | T_ESCAPED     
     | T_EXCEPT       
     | T_EXEC         
     | T_EXECUTE      
     | T_EXCEPTION    
     | T_EXISTS
     | T_EXIT         
     | T_FETCH  
     | T_FIELDS
     | T_FILE     
     | T_FIRST_VALUE     
     | T_FLOAT        
     | T_FOR  
     | T_FORMAT     
     | T_FOUND        
     | T_FROM   
     | T_FULL     
     | T_FUNCTION
     | T_GET
     | T_GLOBAL
     | T_GRANT
     | T_GROUP        
     | T_HANDLER      
     | T_HASH
     | T_HAVING       
     | T_HIVE         
     | T_HOST         
     | T_IF    
     | T_IGNORE     
     | T_IMMEDIATE    
     | T_IN   
     | T_INCLUDE
     | T_INDEX     
     | T_INNER
     | T_INOUT
     | T_INSERT
     | T_INT          
     | T_INTEGER      
     | T_INTERSECT    
     | T_INTO 
     | T_INVOKER     
     | T_ITEMS     
     | T_IS    
     | T_JOIN     
     | T_KEY
     | T_KEYS
     | T_LAG
     | T_LANGUAGE
     | T_LAST_VALUE
     | T_LEAD
     | T_LEAVE     
     | T_LEFT     
     | T_LIKE 
     | T_LIMIT  
     | T_LINES     
     | T_LOCAL     
     | T_LOGGED     
     | T_LOOP    
     | T_MAP  
     | T_MATCHED     
     | T_MAX     
     | T_MERGE
     | T_MESSAGE_TEXT
     | T_MICROSECOND
     | T_MICROSECONDS
     | T_MIN
     | T_MULTISET
     | T_NOT          
     // T_NULL reserved word       
     | T_NUMBER   
     | T_OBJECT     
     | T_ON
     | T_ONLY
     | T_OPEN         
     | T_OR           
     | T_ORDER   
     | T_OUT     
     | T_OUTER
     | T_OVER
     | T_OVERWRITE
     | T_OWNER
     | T_PART_LOC 
     | T_PARTITION     
     | T_PRESERVE
     | T_PRIMARY
     | T_PRINT 
     | T_PROC
     | T_PROCEDURE   
     | T_RANK    
     | T_REGEXP
     | T_RR     
     | T_REPLACE
     | T_RESIGNAL
     | T_RESULT
     | T_RETURN       
     | T_RETURNS
     | T_REVERSE    
     | T_RIGHT
     | T_RLIKE
     | T_RS     
     | T_ROLLBACK
     | T_ROW
     | T_ROWS
     | T_ROW_COUNT
     | T_ROW_NUMBER
     | T_SECURITY
     | T_SEL          
     | T_SELECT       
     | T_SET 
     | T_SETS     
     | T_SIGNAL
     | T_SMALLINT     
     | T_SQL
     | T_SQLEXCEPTION 
     | T_SQLINSERT
     | T_SQLSTATE
     | T_SQLWARNING   
     | T_STEP    
     | T_STDEV     
     | T_STRING    
     | T_SUBSTRING
     | T_SUM
     | T_SYSDATE     
     | T_TABLE
     | T_TEMPORARY
     | T_TERMINATED
     | T_THEN  
     | T_TIMESTAMP     
     | T_TITLE
     | T_TO     
     | T_TOP
     | T_TRIM
     // T_UNION reserved word   
     | T_UNIQUE     
     | T_UPDATE  
     | T_UR     
     | T_USE         
     | T_USER     
     | T_USING        
     | T_VALUE
     | T_VALUES
     | T_VAR
     | T_VARCHAR      
     | T_VARCHAR2
     | T_VARIANCE
     | T_VOLATILE
     // T_WHEN reserved word         
     // T_WHERE reserved word        
     | T_WHILE     
     | T_WITH 
     | T_WITHOUT      
     | T_WORK
     ;

// Lexer rules
T_ALL             : A L L ;
T_ALTER           : A L T E R ;
T_AND             : A N D ;
T_AS              : A S ;
T_ASC             : A S C ;
T_AT              : A T ;
T_AVG             : A V G ; 
T_BATCHSIZE       : B A T C H S I Z E ;
T_BEGIN           : B E G I N ;
T_BETWEEN         : B E T W E E N ; 
T_BIGINT          : B I G I N T ;
T_BREAK           : B R E A K ;
T_BY              : B Y ;
T_CALL            : C A L L ;
T_CALLER          : C A L L E R ;
T_CASE            : C A S E ;
T_CASESPECIFIC    : C A S E S P E C I F I C ; 
T_CAST            : C A S T ;
T_CHAR            : C H A R ;
T_CHARACTER       : C H A R A C T E R ;
T_CLIENT          : C L I E N T ;
T_CLOSE           : C L O S E ;
T_COLLECTION      : C O L L E C T I O N ; 
T_COPY            : C O P Y ;
T_COMMIT          : C O M M I T ; 
T_CONCAT          : C O N C A T;
T_CONDITION       : C O N D I T I O N ;
T_CONSTRAINT      : C O N S T R A I N T ; 
T_CONTINUE        : C O N T I N U E ;
T_COUNT           : C O U N T ;
T_COUNT_BIG       : C O U N T '_' B I G;
T_CREATE          : C R E A T E ;
T_CREATOR         : C R E A T O R ;
T_CS              : C S;
T_CURRENT         : C U R R E N T ;
T_CURSOR          : C U R S O R ;
T_DATE            : D A T E ;
T_DAY             : D A Y ;
T_DAYS            : D A Y S ;
T_DEC             : D E C ;
T_DECIMAL         : D E C I M A L ;
T_DECLARE         : D E C L A R E ;
T_DEFAULT         : D E F A U L T ;
T_DEFINED         : D E F I N E D ; 
T_DEFINER         : D E F I N E R ;
T_DELETE          : D E L E T E ;
T_DELIMITED       : D E L I M I T E D ; 
T_DELIMITER       : D E L I M I T E R ; 
T_DESC            : D E S C ;
T_DIAGNOSTICS     : D I A G N O S T I C S ;
T_DISTINCT        : D I S T I N C T ;
T_DISTRIBUTE      : D I S T R I B U T E ;
T_DO              : D O ;
T_DROP            : D R O P ;
T_DYNAMIC         : D Y N A M I C ; 
T_ELSE            : E L S E ;
T_ELSEIF          : E L S E I F ;
T_ELSIF           : E L S I F ;
T_END             : E N D ;
T_ESCAPED         : E S C A P E D ; 
T_EXCEPT          : E X C E P T ;
T_EXEC            : E X E C ;
T_EXECUTE         : E X E C U T E ;
T_EXCEPTION       : E X C E P T I O N ;
T_EXISTS          : E X I S T S ; 
T_EXIT            : E X I T ;
T_FETCH           : F E T C H ;
T_FIELDS          : F I E L D S ; 
T_FILE            : F I L E ;
T_FLOAT           : F L O A T ;
T_FOR             : F O R ;
T_FORMAT          : F O R M A T ;
T_FOUND           : F O U N D ;
T_FROM            : F R O M ; 
T_FULL            : F U L L ;
T_FUNCTION        : F U N C T I O N ;
T_GET             : G E T ;
T_GLOBAL          : G L O B A L ; 
T_GRANT           : G R A N T ; 
T_GROUP           : G R O U P ;
T_HANDLER         : H A N D L E R ;
T_HASH            : H A S H ;
T_HAVING          : H A V I N G ;
T_HIVE            : H I V E ;
T_HOST            : H O S T ;
T_IF              : I F ;
T_IGNORE          : I G N O R E ; 
T_IMMEDIATE       : I M M E D I A T E ;
T_IN              : I N ;
T_INCLUDE         : I N C L U D E ;
T_INDEX           : I N D E X ;
T_INNER           : I N N E R ; 
T_INOUT           : I N O U T;
T_INSERT          : I N S E R T ;
T_INT             : I N T ;
T_INTEGER         : I N T E G E R ;
T_INTERSECT       : I N T E R S E C T ;
T_INTO            : I N T O ;
T_INVOKER         : I N V O K E R ;
T_IS              : I S ;
T_ITEMS           : I T E M S ; 
T_JOIN            : J O I N ;
T_KEY             : K E Y ;
T_KEYS            : K E Y S ;
T_LANGUAGE        : L A N G U A G E ;
T_LEAVE           : L E A V E ;
T_LEFT            : L E F T ;
T_LIKE            : L I K E ; 
T_LIMIT           : L I M I T ;
T_LINES           : L I N E S ; 
T_LOCAL           : L O C A L ;
T_LOGGED          : L O G G E D ; 
T_LOOP            : L O O P ;
T_MAP             : M A P ; 
T_MATCHED         : M A T C H E D ; 
T_MAX             : M A X ;
T_MERGE           : M E R G E ; 
T_MESSAGE_TEXT    : M E S S A G E '_' T E X T ;
T_MICROSECOND     : M I C R O S E C O N D ;
T_MICROSECONDS    : M I C R O S E C O N D S;
T_MIN             : M I N ;
T_MULTISET        : M U L T I S E T ; 
T_NOT             : N O T ;
T_NULL            : N U L L ;
T_NUMBER          : N U M B E R ;
T_OBJECT          : O B J E C T ; 
T_ON              : O N ;
T_ONLY            : O N L Y ;
T_OPEN            : O P E N ;
T_OR              : O R ;
T_ORDER           : O R D E R;
T_OUT             : O U T ;
T_OUTER           : O U T E R ;
T_OVER            : O V E R ;
T_OVERWRITE       : O V E R W R I T E ; 
T_OWNER           : O W N E R ; 
T_PARTITION       : P A R T I T I O N ; 
T_PRESERVE        : P R E S E R V E ; 
T_PRIMARY         : P R I M A R Y ;
T_PRINT           : P R I N T ; 
T_PROC            : P R O C ;
T_PROCEDURE       : P R O C E D U R E;
T_REGEXP          : R E G E X P ;
T_REPLACE         : R E P L A C E ; 
T_RESIGNAL        : R E S I G N A L ;
T_RESULT          : R E S U L T ; 
T_RETURN          : R E T U R N ;
T_RETURNS         : R E T U R N S ;
T_REVERSE         : R E V E R S E ;
T_RIGHT           : R I G H T ;
T_RLIKE           : R L I K E ;
T_ROLLBACK        : R O L L B A C K ;
T_ROW             : R O W ; 
T_ROWS            : R O W S ; 
T_ROW_COUNT       : R O W '_' C O U N T ;
T_RR              : R R;
T_RS              : R S ;
T_TRIM            : T R I M ;
T_SECURITY        : S E C U R I T Y ; 
T_SEL             : S E L ;
T_SELECT          : S E L E C T ; 
T_SET             : S E T ;
T_SETS            : S E T S;
T_SIGNAL          : S I G N A L ;
T_SMALLINT        : S M A L L I N T ;
T_SQL             : S Q L ; 
T_SQLEXCEPTION    : S Q L E X C E P T I O N ;
T_SQLINSERT       : S Q L I N S E R T ;
T_SQLSTATE        : S Q L S T A T E ;
T_SQLWARNING      : S Q L W A R N I N G ;
T_STEP            : S T E P ; 
T_STRING          : S T R I N G ;
T_SUBSTRING       : S U B S T R I N G ; 
T_SUM             : S U M ;
T_TABLE           : T A B L E ;
T_TEMPORARY       : T E M P O R A R Y ;
T_TERMINATED      : T E R M I N A T E D ; 
T_THEN            : T H E N ;
T_TIMESTAMP       : T I M E S T A M P ;
T_TITLE           : T I T L E ;
T_TO              : T O ; 
T_TOP             : T O P ;
T_UNION           : U N I O N ;
T_UNIQUE          : U N I Q U E ;
T_UPDATE          : U P D A T E ; 
T_UR              : U R ;
T_USE             : U S E ;
T_USING           : U S I N G ;
T_VALUE           : V A L U E ;
T_VALUES          : V A L U E S ;
T_VAR             : V A R ;
T_VARCHAR         : V A R C H A R ;
T_VARCHAR2        : V A R C H A R '2' ;
T_VOLATILE        : V O L A T I L E ;
T_WHEN            : W H E N ;
T_WHERE           : W H E R E ;
T_WHILE           : W H I L E ;
T_WITH            : W I T H ; 
T_WITHOUT         : W I T H O U T ;
T_WORK            : W O R K ;

// Functions with specific syntax
T_ACTIVITY_COUNT       : A C T I V I T Y '_' C O U N T ;
T_CURRENT_DATE         : C U R R E N T '_' D A T E ;
T_CURRENT_TIMESTAMP    : C U R R E N T '_' T I M E S T A M P ;
T_CURRENT_USER         : C U R R E N T '_' U S E R ;
T_DENSE_RANK           : D E N S E '_' R A N K ;
T_FIRST_VALUE          : F I R S T '_' V A L U E; 
T_LAG                  : L A G ;
T_LAST_VALUE           : L A S T '_' V A L U E; 
T_LEAD                 : L E A D ; 
T_MAX_PART_STRING      : M A X '_' P A R T '_' S T R I N G ;
T_MIN_PART_STRING      : M I N '_' P A R T '_' S T R I N G ;
T_MAX_PART_INT         : M A X '_' P A R T '_' I N T ;
T_MIN_PART_INT         : M I N '_' P A R T '_' I N T ;
T_MAX_PART_DATE        : M A X '_' P A R T '_' D A T E ;
T_MIN_PART_DATE        : M I N '_' P A R T '_' D A T E ;
T_PART_LOC             : P A R T '_' L O C ;
T_RANK                 : R A N K ;
T_ROW_NUMBER           : R O W '_' N U M B E R;
T_STDEV                : S T D E V ;
T_SYSDATE              : S Y S D A T E ;
T_VARIANCE             : V A R I A N C E ; 
T_USER                 : U S E R; 

T_ADD          : '+' ;
T_COLON        : ':' ;
T_COMMA        : ',' ;
T_PIPE         : '||' ;
T_DIV          : '/' ;
T_DOT2         : '..' ;
T_EQUAL        : '=' ;
T_EQUAL2       : '==' ;
T_NOTEQUAL     : '<>' ;
T_NOTEQUAL2    : '!=' ;
T_GREATER      : '>' ;
T_GREATEREQUAL : '>=' ;
T_LESS         : '<' ;
T_LESSEQUAL    : '<=' ;
T_MUL          : '*' ;
T_OPEN_B       : '{' ;
T_OPEN_P       : '(' ;
T_CLOSE_B      : '}' ; 
T_CLOSE_P      : ')' ;
T_SEMICOLON    : ';' ;
T_SUB          : '-' ;

P_e            : '-e' ;
P_f            : '-f' ;
P_hiveconf     : '-hiveconf' ;
P_i            : '-i' ;
P_S            : '-S' ;
P_h            : '-h' ;

L_ID        : L_ID_PART (L_BLANK* '.' L_BLANK* L_ID_PART)*             // Identifier
            ;
L_S_STRING  : '\'' (('\'' '\'') | ('\\' '\'') | ~('\''))* '\''         // Single quoted string literal
            ;
L_D_STRING  : '"' (L_STR_ESC_D | .)*? '"'                              // Double quoted string literal
            ;
L_INT       : L_DIGIT+ ;                                               // Integer
L_DEC       : L_DIGIT+ '.' ~'.' L_DIGIT*                               // Decimal number
            | '.' L_DIGIT+
            ;
L_WS        : L_BLANK+ -> skip ;                                       // Whitespace
L_M_COMMENT : '/*' .*? '*/' -> channel(HIDDEN) ;                       // Multiline comment
L_S_COMMENT : ('--' | '//')  .*? '\r'? '\n' -> channel(HIDDEN) ;       // Single line comment

L_FILE      : '/'? L_ID ('/' L_ID)*                                    // File path
            | ([a-zA-Z] ':' '\\'?)? L_ID ('\\' L_ID)*
            ; 

L_LABEL     : ([a-zA-Z] | L_DIGIT | '_')* ':'            
            ;
            
fragment
L_ID_PART  :
             [a-zA-Z] ([a-zA-Z] | L_DIGIT | '_')*                           // Identifier part
            | ('_' | '@' | ':' | '#' | '$') ([a-zA-Z] | L_DIGIT | '_' | '@' | ':' | '#' | '$')+     // (at least one char must follow special char)
            | '"' .*? '"'                                                   // Quoted identifiers
            | '[' .*? ']'
            | '`' .*? '`'
            ;
fragment
L_STR_ESC_D :                                                          // Double quoted string escape sequence
              '""' | '\\"' 
            ;            
fragment
L_DIGIT     : [0-9]                                                    // Digit
            ;
fragment
L_BLANK     : (' ' | '\t' | '\r' | '\n')
            ;

// Support case-insensitive keywords and allowing case-sensitive identifiers
fragment A : ('a'|'A') ;
fragment B : ('b'|'B') ;
fragment C : ('c'|'C') ;
fragment D : ('d'|'D') ;
fragment E : ('e'|'E') ;
fragment F : ('f'|'F') ;
fragment G : ('g'|'G') ;
fragment H : ('h'|'H') ;
fragment I : ('i'|'I') ;
fragment J : ('j'|'J') ;
fragment K : ('k'|'K') ;
fragment L : ('l'|'L') ;
fragment M : ('m'|'M') ;
fragment N : ('n'|'N') ;
fragment O : ('o'|'O') ;
fragment P : ('p'|'P') ;
fragment Q : ('q'|'Q') ;
fragment R : ('r'|'R') ;
fragment S : ('s'|'S') ;
fragment T : ('t'|'T') ;
fragment U : ('u'|'U') ;
fragment V : ('v'|'V') ;
fragment W : ('w'|'W') ;
fragment X : ('x'|'X') ;
fragment Y : ('y'|'Y') ;
fragment Z : ('z'|'Z') ;
