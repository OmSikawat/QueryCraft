package com.qc.enterprise.service;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Service;

@Service
public class QueryValidatorService {

    public void validateSafeSelect(String sql) {
        try {
            // 1. Parse the SQL into an Abstract Syntax Tree (AST)
            Statement statement = CCJSqlParserUtil.parse(sql);

            // 2. Mathematically verify it is ONLY a Select statement
            if (!(statement instanceof Select)) {
                throw new SecurityException("CRITICAL: AI attempted a prohibited database operation. Only SELECT is allowed. Query blocked: " + sql);
            }

            // Note: In an enterprise system, you could also check the AST
            // to ensure they aren't querying blocked column names here.

        } catch (Exception e) {
            throw new SecurityException("Failed to parse SQL. Query blocked to prevent injection. Error: " + e.getMessage());
        }
    }
}