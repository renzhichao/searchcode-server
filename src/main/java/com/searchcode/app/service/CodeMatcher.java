/*
 * Copyright (c) 2016 Boyter Online Services
 *
 * Use of this software is governed by the Fair Source License included
 * in the LICENSE.TXT file, but will be eventually open under GNU General Public License Version 3
 * see the README.md for when this clause will take effect
 *
 * Version 1.3.15
 */

package com.searchcode.app.service;

import com.searchcode.app.config.Values;
import com.searchcode.app.dao.Data;
import com.searchcode.app.dto.CodeMatchResult;
import com.searchcode.app.dto.CodeResult;
import com.searchcode.app.util.LoggerWrapper;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Responsible for formatting the code results so they appear nicely with relevant lines. Changes to anything in
 * here need to be made with great care for performance as this is the "hottest" collection of methods with regards
 * to user performance.
 */
public class CodeMatcher {

    private final LoggerWrapper logger;
    public int MATCHLINES, MAXLINEDEPTH;

    public CodeMatcher() {
        this(Singleton.getData(), Singleton.getLogger());
    }

    public CodeMatcher(Data data, LoggerWrapper logger) {
        this.MATCHLINES = Singleton.getHelpers().tryParseInt(data.getDataByName(Values.MATCHLINES, Values.DEFAULTMATCHLINES), Values.DEFAULTMATCHLINES);
        this.MAXLINEDEPTH = Singleton.getHelpers().tryParseInt(data.getDataByName(Values.MAXLINEDEPTH, Values.DEFAULTMAXLINEDEPTH), Values.DEFAULTMAXLINEDEPTH);
        this.logger = logger;
    }

    /**
     * Entry point for matching lines
     */
    public ArrayList<CodeResult> formatResults(List<CodeResult> codeResult, String matchTerms, boolean highlightLine) {
        var lstMatchTerms = this.splitTerms(matchTerms);

        var results = new ArrayList<CodeResult>();

        for (var code : codeResult) {
            var result = this.matchResults(code.getCode(), lstMatchTerms, highlightLine);

            if (result != null) {
                code.setMatchingResults(result);
                results.add(code);
            }
        }

        return results;
    }

    /**
     * Actually does the matching for a single code result given the match terms
     */
    public ArrayList<CodeMatchResult> matchResults(List<String> code, ArrayList<String> matchTerms, boolean highlightLine) {
        var resultLines = this.findMatchingLines(code, matchTerms, highlightLine);
        var newResultLines = new ArrayList<CodeMatchResult>();

        // get the top matching lines for this result
        resultLines.sort((p1, p2) -> Integer.valueOf(p2.getLineMatches()).compareTo(p1.getLineMatches()));

        // gets the best snippets based on number of matches
        for (var i = 0; i < resultLines.size(); i++) {
            var match = resultLines.get(i);
            match.setLineNumber(match.getLineNumber() + 1);

            if (!resultExists(newResultLines, match.getLineNumber())) {
                newResultLines.add(match);
            }

            var resultBefore = getResultByLineNumber(resultLines, match.getLineNumber() - 1);
            var resultAfter = getResultByLineNumber(resultLines, match.getLineNumber() + 1);

            if (resultBefore != null && !resultExists(newResultLines, match.getLineNumber() - 1)) {
                newResultLines.add(resultBefore);
            }
            if (resultAfter != null && !resultExists(newResultLines, match.getLineNumber() + 1)) {
                newResultLines.add(resultAfter);
            }

            if (newResultLines.size() >= MATCHLINES) {
                break;
            }
        }

        newResultLines.sort((p1, p2) -> Integer.valueOf(p1.getLineNumber()).compareTo(p2.getLineNumber()));

        if (!newResultLines.isEmpty()) {
            newResultLines.get(0).addBreak = false;
            return newResultLines;
        }

        return null;
    }

    /**
     * If changing anything in here be wary of performance issues as it is the slowest method by a long shot.
     * Be especially careful of branch prediction issues which is why this method has been re-written several times
     * just to avoid those issues even though the result was a LONGER method
     * TODO wring more performance out of this method where possible
     */
    public List<CodeMatchResult> findMatchingLines(List<String> code, ArrayList<String> matchTerms, boolean highlightLine) {
        var resultLines = new ArrayList<CodeMatchResult>();

        int codesize = code.size();
        int searchThrough = codesize > this.MAXLINEDEPTH ? this.MAXLINEDEPTH : codesize;
        int matching = 0;

        // Go through each line finding matching lines
        for (var i = 0; i < searchThrough; i++) {
            var matchRes = code.get(i).toLowerCase().replaceAll("\\s+", " ");
            matching = 0;

            for (var matchTerm : matchTerms) {
                if (matchRes.contains(matchTerm.replace("*", ""))) {
                    matching++;
                }
            }

            if (matching != 0) {
                resultLines.add(new CodeMatchResult(code.get(i), true, false, matching, i));
            }
        }

        // Get the adjacent lines
        var adjacentLines = new ArrayList<CodeMatchResult>();
        for (var cmr : resultLines) {
            int linenumber = cmr.getLineNumber();
            int previouslinenumber = linenumber - 1;
            int nextlinenumber = linenumber + 1;

            if (previouslinenumber >= 0 && !this.resultExists(resultLines, previouslinenumber)) {
                adjacentLines.add(new CodeMatchResult(code.get(previouslinenumber), false, false, 0, previouslinenumber));
            }

            if (nextlinenumber < codesize && !this.resultExists(resultLines, nextlinenumber)) {
                adjacentLines.add(new CodeMatchResult(code.get(nextlinenumber), false, false, 0, nextlinenumber));
            }
        }

        resultLines.addAll(adjacentLines);

        // If not matching we probably matched on the filename or past 10000
        if (resultLines.isEmpty()) {
            searchThrough = codesize > MATCHLINES ? MATCHLINES : codesize;

            for (int i = 0; i < searchThrough; i++) {
                resultLines.add(new CodeMatchResult(code.get(i), false, false, 0, i));
            }
        }

        // Highlight the lines if required but always escape everything
        if (highlightLine) {
            for (var cmr : resultLines) {
                if (cmr.isMatching()) {
                    String line = Values.EMPTYSTRING;
                    try {
                        line = this.highlightLine(cmr.getLine(), matchTerms);
                    } catch (StringIndexOutOfBoundsException ex) {
                        this.logger.severe(String.format("3d15e6ed::error in class %s exception %s unable to highlight line %s with terms %s", ex.getClass(), ex.getMessage(), cmr.getLine(), String.join(",", matchTerms)));
                    }
                    cmr.setLine(line);
                } else {
                    cmr.setLine(StringEscapeUtils.escapeHtml4(cmr.getLine()));
                }
            }
        } else {
            for (var cmr : resultLines) {
                cmr.setLine(StringEscapeUtils.escapeHtml4(cmr.getLine()));
            }
        }

        return resultLines;
    }

    // TODO Investigate issues such as "List<String> test = *p;"
    // which produces string> and <string which is not what
    // we want although they are cleared later so not a huge issue
    public ArrayList<String> splitTerms(String matchTerms) {
        var splitMatchTerms = new ArrayList<String>();
        var newTerms = new ArrayList<String>();

        for (var s : matchTerms.trim().split(" ")) {
            if (!s.isEmpty()) {
                switch (s) {
                    case "AND":
                    case "OR":
                    case "NOT":
                        splitMatchTerms.add(s);
                        break;
                    default:
                        splitMatchTerms.add(s.toLowerCase());
                }
            }
        }

        for (var s : splitMatchTerms) {
            for (var t : s.split("\\.")) {
                if (!t.isEmpty()) {
                    newTerms.add(t);
                }
            }
            for (var t : s.split("\\(")) {
                if (!t.isEmpty()) {
                    newTerms.add(t);
                }
            }
            for (var t : s.split("\\-")) {
                if (!t.isEmpty()) {
                    newTerms.add(t);
                }
            }
            for (var t : s.split("<")) {
                if (!t.isEmpty()) {
                    newTerms.add(t);
                }
            }
            for (var t : s.split(">")) {
                if (!t.isEmpty()) {
                    newTerms.add(t);
                }
            }
            newTerms.add(s);
        }

        // Remove duplicates
        var depdupeTerms = new ArrayList<>(new LinkedHashSet<>(newTerms));
        // Sort largest to smallest to produce largest matching results
        depdupeTerms.sort((p1, p2) -> Integer.valueOf(p2.length()).compareTo(p1.length()));
        return depdupeTerms;
    }

    /**
     * Given a string and the terms we want to highlight attempts to parse it apart and surround the matching
     * terms with <strong> tags.
     * TODO a bug exists here, see test cases for details
     */
    public String highlightLine(String line, ArrayList<String> matchTerms) throws StringIndexOutOfBoundsException {
        var terms = matchTerms.stream()
                .filter(s -> !"AND".equals(s) && !"OR".equals(s) && !"NOT".equals(s))
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        var tokens = line.split(" ");
        var returnList = new ArrayList<String>();

        for (var token : tokens) {
            var longestTerm = Values.EMPTYSTRING;
            var tokenLowercase = token.toLowerCase();

            for (var term : terms) {
                // Find the longest matching
                if (term.replace(")", "").endsWith("*")) {
                    if (tokenLowercase.contains(term.replace(")", "").replace("*", ""))) {
                        if (term.length() > longestTerm.length()) {
                            longestTerm = term;
                        }
                    }
                } else {
                    if (tokenLowercase.contains(term)) {
                        if (term.length() > longestTerm.length()) {
                            longestTerm = term;
                        }
                    }
                }
            }

            if (!Values.EMPTYSTRING.equals(longestTerm)) {
                if (longestTerm.replace(")", "").endsWith("*")) {
                    var loc = tokenLowercase.indexOf(longestTerm.replace(")", "").replace("*", ""));

                    returnList.add(StringEscapeUtils.escapeHtml4(
                            token.substring(0, loc)) +
                            "<strong>" +
                            StringEscapeUtils.escapeHtml4(token.substring(loc)) +
                            "</strong>");
                } else {
                    var loc = tokenLowercase.indexOf(longestTerm);

                    returnList.add(StringEscapeUtils.escapeHtml4(
                            token.substring(0, loc)) +
                            "<strong>" +
                            StringEscapeUtils.escapeHtml4(token.substring(loc, loc + longestTerm.length())) +
                            "</strong>" +
                            this.highlightLine(token.substring(loc + longestTerm.length()), matchTerms));
                }
            } else {
                returnList.add(StringEscapeUtils.escapeHtml4(token));
            }
        }

        return StringUtils.join(returnList, " ");
    }

    /**
     * Helper to check if result exists in the collection based on line number
     */
    private boolean resultExists(List<CodeMatchResult> lst, int value) {
        for (var s : lst) {
            if (s.getLineNumber() == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * Helper to pull value out of the list based on line number
     */
    private CodeMatchResult getResultByLineNumber(List<CodeMatchResult> lst, int value) {
        for (var s : lst) {
            if (s.getLineNumber() == value) {
                return s;
            }
        }
        return null;
    }
}