/**
 * Copyright (c) 2002-2014 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.server.webadmin.console;

import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.impls.neo4j2.Neo4j2Graph;
import groovy.lang.Binding;
import groovy.lang.GroovyRuntimeException;
import org.codehaus.groovy.tools.shell.IO;
import org.neo4j.graphdb.Transaction;
import org.neo4j.helpers.Pair;
import org.neo4j.server.database.Database;
import org.neo4j.server.logging.Logger;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GremlinSession implements ScriptSession {
    private static final String INIT_FUNCTION = "init()";
    private static final Logger log = Logger.getLogger(GremlinSession.class);
    private final Database database;
    private final IO io;
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private final List<String> initialBindings;
    protected GremlinWebConsole scriptEngine;

    public GremlinSession(Database database) {
        this.database = database;
        PrintStream out = new PrintStream(new BufferedOutputStream(baos));

        io = new IO(System.in, out, out);

        Map<String, Object> bindings = new HashMap<String, Object>();
        bindings.put("g", getGremlinWrappedGraph());
        bindings.put("out", out);

        initialBindings = new ArrayList<String>(bindings.keySet());

        try {
            scriptEngine = new GremlinWebConsole(new Binding(bindings), io);
        } catch (final Exception failure) {
            scriptEngine = new GremlinWebConsole() {
                @Override
                public void execute(String script) {
                    io.out.println("Could not start Groovy during Gremlin initialization, reason:");
                    failure.printStackTrace(io.out);
                }
            };
        }
    }

    /**
     * Take some gremlin script, evaluate it in the context of this gremlin
     * session, and return the result.
     *
     * @param script
     * @return the return string of the evaluation result, or the exception
     *         message.
     */
    @Override
    public Pair<String, String> evaluate(String script) {
        String result = null;
        try (Transaction tx = database.getGraph().beginTx()) {
            if (script.equals(INIT_FUNCTION)) {
                result = init();
            } else {
                try {
                    scriptEngine.execute(script);
                    result = baos.toString();
                } finally {
                    resetIO();
                }
            }
            tx.success();
        } catch (GroovyRuntimeException ex) {
            log.error(ex);
            result = ex.getMessage();
        }
        return Pair.of(result, null);
    }

    private String init() {
        StringBuilder out = new StringBuilder();
        out.append("\n");
        out.append("         \\,,,/\n");
        out.append("         (o o)\n");
        out.append("-----oOOo-(_)-oOOo-----\n");
        out.append("\n");

        out.append("Available variables:\n");
        for (String variable : initialBindings) {
            out.append("  " + variable + "\t= ");
            out.append(evaluate(variable));
        }
        out.append("\n");

        return out.toString();
    }

    private void resetIO() {
        baos.reset();
    }

    private TransactionalGraph getGremlinWrappedGraph() {
        Neo4j2Graph neo4jGraph = null;
        try {
            neo4jGraph = new Neo4j2Graph(database.getGraph());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return neo4jGraph;
    }
}
