package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BrokerAgent extends Agent {
    // List of all available resource agents
    private static final String[] ALL_AGENTS = {
            "WikipediaAgent",
            "DuckDuckGoAgent",
            "WikidataAgent",
            "OpenRouterAgent",
            "LangsearchAgent",
            "TogetherAgent",
            "DeepInfraAgent",
    };

    private static final String[] BOOK_AGENTS = {
            "BookSearchAgent"
    };

    private static final String[] MATH_AGENTS = {
            "WolframAlphaAgent"
    };

    // Agents that should be excluded when adding specialty agents
    private static final String[] EXCLUDED_AGENTS = {
            "WikipediaAgent",
            "DuckDuckGoAgent",
            "WikidataAgent"
    };

    protected void setup() {
        System.out.println("BrokerAgent " + getAID().getName() + " is ready.");

        addBehaviour(new CyclicBehaviour(this) {
            private final Map<String, String> contextMap = new ConcurrentHashMap<>();
            private final Set<String> activeAgents = new HashSet<>();

            public void action() {
                ACLMessage msg = receive(MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
                if (msg != null) {
                    String query = msg.getContent();
                    System.out.println("Broker received query: " + query);

                    // Update context with previous interactions
                    updateContextFromQuery(query);

                    if (isComplexQuery(query)) {
                        System.out.println("Detected COMPLEX query");
                        handleComplexQuery(msg);
                    } else {
                        System.out.println("Detected SIMPLE query");
                        handleSimpleQuery(msg);
                    }
                } else {
                    block();
                }
            }

            private void updateContextFromQuery(String query) {
                // Extract named entities and store in context
                if (query.matches(".*\\b(Steven Spielberg|Albert Einstein|Elon Musk)\\b.*")) {
                    String subject = query.replaceAll(".*\\b(Steven Spielberg|Albert Einstein|Elon Musk)\\b.*", "$1");
                    contextMap.put("current_subject", subject);
                    System.out.println("Updated context with subject: " + subject);
                }
            }

            private boolean isComplexQuery(String query) {
                String lowerQuery = query.toLowerCase();
                return lowerQuery.matches(".*\\b(and|or|but|then|also|as well as|before|after|while|meanwhile)\\b.*") ||
                        lowerQuery.contains(",") ||
                        lowerQuery.contains(";") ||
                        lowerQuery.split("\\?").length > 1 ||
                        lowerQuery.matches(".*\\b(list|compare|difference|between|advantages|disadvantages|pros|cons)\\b.*") ||
                        (lowerQuery.matches(".*\\b(he|she|they|it)\\b.*") && contextMap.containsKey("current_subject"));
            }

            private List<String> decomposeQuery(String query) {
                List<String> subQueries = new ArrayList<>();

                // 1. First split by conjunctions and punctuation
                String[] rawParts = query.split("(?i)\\b(and|or|but|then|also|,|;|vs\\.?|versus)\\b");

                // 2. Process each part
                for (String part : rawParts) {
                    part = part.trim();

                    // Skip empty parts
                    if (part.isEmpty()) {
                        continue;
                    }

                    // 3. Further split if contains multiple questions
                    if (part.contains("?")) {
                        String[] questions = part.split("\\?");
                        for (String q : questions) {
                            q = q.trim();
                            if (!q.isEmpty()) {
                                subQueries.add(q + "?"); // Put back the question mark
                            }
                        }
                    }
                    // 4. Handle comparison/listing patterns
                    else if (part.matches(".*\\b(compare|difference|between|advantages|disadvantages|pros|cons|list)\\b.*")) {
                        subQueries.addAll(splitComparativeQuery(part));
                    }
                    // 5. Add regular parts
                    else {
                        subQueries.add(part);
                    }
                }

                // 6. If we couldn't decompose, return the original as single subquery
                if (subQueries.isEmpty()) {
                    subQueries.add(query);
                }

                return subQueries;
            }

            private List<String> splitComparativeQuery(String query) {
                List<String> parts = new ArrayList<>();

                // Handle "A vs B" pattern
                if (query.matches(".*\\bvs\\.?|versus\\b.*")) {
                    String[] sides = query.split("\\bvs\\.?|versus\\b");
                    if (sides.length == 2) {
                        parts.add("What are the characteristics of " + sides[0].trim() + "?");
                        parts.add("What are the characteristics of " + sides[1].trim() + "?");
                        parts.add("Compare " + sides[0].trim() + " and " + sides[1].trim());
                        return parts;
                    }
                }

                // Handle "difference between A and B"
                Matcher diffMatcher = Pattern.compile("difference between (.*?) and (.*)").matcher(query);
                if (diffMatcher.find()) {
                    parts.add("Describe " + diffMatcher.group(1).trim());
                    parts.add("Describe " + diffMatcher.group(2).trim());
                    parts.add("What is the difference between " + diffMatcher.group(1).trim() +
                            " and " + diffMatcher.group(2).trim() + "?");
                    return parts;
                }

                // Default handling for other comparative queries
                parts.add(query);
                return parts;
            }

//            private void handleComplexQuery(ACLMessage originalMsg) {
//                String query = originalMsg.getContent();
//                String resolvedQuery = resolvePronouns(query);
//                System.out.println("Processing complex query with selected agents: " + resolvedQuery);
//
//
//
//                // Use only these three agents for complex queries
//                String[] agentsToQuery = {"OpenRouterAgent",  "TogetherAgent", "DeepInfraAgent","LangsearchAgent"};
//
//                // Create a map to store results by agent
//                Map<String, String> resultsByAgent = new ConcurrentHashMap<>();
//                List<String> agentsWithTimeout = new ArrayList<>();
//                List<String> agentsWithError = new ArrayList<>();
//
//                // Query all selected agents in parallel
//                ExecutorService executor = Executors.newFixedThreadPool(agentsToQuery.length);
//                CountDownLatch latch = new CountDownLatch(agentsToQuery.length);
//
//                for (String agentName : agentsToQuery) {
//                    executor.execute(() -> {
//                        try {
//                            String processedQuery = agentSpecificPreprocessing(resolvedQuery, agentName);
//                            System.out.println("Sending to " + agentName + ": " + processedQuery);
//
//                            ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
//                            request.addReceiver(new AID(agentName, AID.ISLOCALNAME));
//                            request.setContent(processedQuery);
//                            send(request);
//
//                            // Wait for response with timeout
//                            ACLMessage reply = blockingReceive(
//                                    MessageTemplate.and(
//                                            MessageTemplate.MatchPerformative(ACLMessage.INFORM),
//                                            MessageTemplate.MatchSender(new AID(agentName, AID.ISLOCALNAME))
//                                    ),
//                                    15000 // 15 seconds timeout
//                            );
//
//                            if (reply != null) {
//                                System.out.println("Received from " + agentName + ": " + reply.getContent());
//                                resultsByAgent.put(agentName, reply.getContent());
//                            } else {
//                                System.out.println("Timeout waiting for " + agentName);
//                                agentsWithTimeout.add(agentName);
//                            }
//                        } catch (Exception e) {
//                            System.out.println("Error with " + agentName + ": " + e.getMessage());
//                            agentsWithError.add(agentName);
//                        } finally {
//                            latch.countDown();
//                        }
//                    });
//                }
//
//                try {
//                    if (!latch.await(20, TimeUnit.SECONDS)) {
//                        System.out.println("Overall query timeout occurred");
//                    }
//                } catch (InterruptedException e) {
//                    System.out.println("Query interrupted");
//                } finally {
//                    executor.shutdown();
//                }
//
//                // Process and combine results
//                StringBuilder combinedResults = new StringBuilder();
//                combinedResults.append("=== Combined Results from Specialized Agents ===\n\n");
//
//                for (String agentName : agentsToQuery) {
//                    String result = resultsByAgent.get(agentName);
//                    if (result != null && isValidResponse(result, agentName)) {
//                        combinedResults.append(formatResponse(agentName, result)).append("\n\n");
//                    }
//                }
//
//                // Send combined response
//                ACLMessage reply = originalMsg.createReply();
//                reply.setPerformative(ACLMessage.INFORM);
//
//                if (combinedResults.length() > 0) {
//                    reply.setContent(combinedResults.toString());
//                    // Cache the successful response
//                    KnowledgeStorage.store(resolvedQuery, combinedResults.toString());
//                } else {
//                    // Provide detailed error information
//                    StringBuilder errorMsg = new StringBuilder("No valid results found from specialized agents. ");
//                    if (!agentsWithTimeout.isEmpty()) {
//                        errorMsg.append("Timeouts from: ").append(agentsWithTimeout).append(". ");
//                    }
//                    if (!agentsWithError.isEmpty()) {
//                        errorMsg.append("Errors from: ").append(agentsWithError).append(". ");
//                    }
//                    reply.setContent(errorMsg.toString());
//                }
//
//                send(reply);
//            }
private void handleComplexQuery(ACLMessage originalMsg) {
    String query = originalMsg.getContent();
    String resolvedQuery = resolvePronouns(query);
    System.out.println("Processing complex query: " + resolvedQuery);

    // 1. First decompose the query
    List<String> subQueries = decomposeQuery(resolvedQuery);
    System.out.println("Divided into subqueries:");
    for (int i = 0; i < subQueries.size(); i++) {
        System.out.println("Subquery " + (i+1) + ": " + subQueries.get(i));
    }

    // 2. Agents to use for each subquery
    String[] agentsToQuery = {"OpenRouterAgent", "TogetherAgent", "DeepInfraAgent", "LangsearchAgent"};

    // 3. Structure to store all results
    Map<String, Map<String, String>> allResults = new LinkedHashMap<>(); // Preserves order
    List<String> globalTimeouts = new ArrayList<>();
    List<String> globalErrors = new ArrayList<>();

    // 4. Process each subquery through all agents
    ExecutorService executor = Executors.newFixedThreadPool(subQueries.size());
    CountDownLatch subqueryLatch = new CountDownLatch(subQueries.size());

    for (String subQuery : subQueries) {
        executor.execute(() -> {
            Map<String, String> subQueryResults = new ConcurrentHashMap<>();
            CountDownLatch agentLatch = new CountDownLatch(agentsToQuery.length);

            for (String agentName : agentsToQuery) {
                new Thread(() -> {
                    try {
                        String processedQuery = agentSpecificPreprocessing(subQuery, agentName);
                        System.out.println("Processing '"+subQuery+"' with "+agentName);

                        ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                        request.addReceiver(new AID(agentName, AID.ISLOCALNAME));
                        request.setContent(processedQuery);
                        send(request);

                        ACLMessage reply = blockingReceive(
                                MessageTemplate.and(
                                        MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                                        MessageTemplate.MatchSender(new AID(agentName, AID.ISLOCALNAME))
                                ),
                                20000 // 20 seconds per agent
                        );

                        if (reply != null) {
                            subQueryResults.put(agentName, reply.getContent());
                        } else {
                            globalTimeouts.add(agentName+" (subquery: '"+subQuery+"')");
                        }
                    } catch (Exception e) {
                        globalErrors.add(agentName+" (subquery: '"+subQuery+"'): "+e.getMessage());
                    } finally {
                        agentLatch.countDown();
                    }
                }).start();
            }

            try {
                agentLatch.await(25, TimeUnit.SECONDS); // Slightly longer than individual timeout
                allResults.put(subQuery, subQueryResults);
            } catch (InterruptedException e) {
                System.out.println("Interrupted while processing subquery: " + subQuery);
            }
            subqueryLatch.countDown();
        });
    }

    // 5. Wait for all subqueries to complete
    try {
        if (!subqueryLatch.await(60, TimeUnit.SECONDS)) {
            System.out.println("Overall complex query timeout");
        }
    } catch (InterruptedException e) {
        System.out.println("Complex query processing interrupted");
    } finally {
        executor.shutdown();
    }

    // 6. Build comprehensive output
    StringBuilder finalOutput = new StringBuilder();
    finalOutput.append("=== Decomposed Query Results ===\n\n");

    // 6a. Add results per subquery
    for (Map.Entry<String, Map<String, String>> entry : allResults.entrySet()) {
        finalOutput.append("\n──── Subquery: \"").append(entry.getKey()).append("\" ────\n");

        boolean hasValidResults = false;
        for (String agentName : agentsToQuery) {
            String result = entry.getValue().get(agentName);
            if (result != null && isValidResponse(result, agentName)) {
                finalOutput.append(formatResponse(agentName, result)).append("\n\n");
                hasValidResults = true;
            }
        }

        if (!hasValidResults) {
            finalOutput.append("No valid results for this subquery\n");
        }
    }

    // 6b. Add summary
    finalOutput.append("\n=== Execution Summary ===\n");
    finalOutput.append("Processed ").append(subQueries.size()).append(" subqueries\n");
    finalOutput.append("Successful responses: ").append(allResults.values().stream()
            .mapToInt(Map::size)
            .sum()).append("\n");

    if (!globalTimeouts.isEmpty()) {
        finalOutput.append("Timeouts:\n");
        globalTimeouts.forEach(timeout -> finalOutput.append("- ").append(timeout).append("\n"));
    }

    if (!globalErrors.isEmpty()) {
        finalOutput.append("Errors:\n");
        globalErrors.forEach(error -> finalOutput.append("- ").append(error).append("\n"));
    }

    // 7. Send response
    ACLMessage reply = originalMsg.createReply();
    reply.setPerformative(ACLMessage.INFORM);
    reply.setContent(finalOutput.toString());
    send(reply);

    // 8. Cache results
    KnowledgeStorage.store(resolvedQuery, finalOutput.toString());
}

            private String resolvePronouns(String query) {
                if (contextMap.containsKey("current_subject")) {
                    String resolved = query.replaceAll("\\b(he|she|they|it)\\b", contextMap.get("current_subject"));
                    System.out.println("Resolved pronouns: " + resolved);
                    return resolved;
                }
                return query;
            }

            private void handleSimpleQuery(ACLMessage originalMsg) {
                String query = originalMsg.getContent();
                String resolvedQuery = resolvePronouns(query);
                System.out.println("Processing simple query: " + resolvedQuery);

                // First check internal knowledge
                String cachedResponse = KnowledgeStorage.retrieve(resolvedQuery);
                if (cachedResponse != null) {
                    System.out.println("Returning cached response");
                    ACLMessage reply = originalMsg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent("Cached Result:\n" + cachedResponse);
                    send(reply);
                    return;
                }

                // Determine which agents should process this query
                String[] agentsToQuery = determineAgentsForQuery(resolvedQuery);
                System.out.println("Selected agents: " + Arrays.toString(agentsToQuery));

                // Create a map to store results by agent
                Map<String, String> resultsByAgent = new ConcurrentHashMap<>();
                List<String> agentsWithTimeout = new ArrayList<>();
                List<String> agentsWithError = new ArrayList<>();

                // Query all resources in parallel
                ExecutorService executor = Executors.newFixedThreadPool(agentsToQuery.length);
                CountDownLatch latch = new CountDownLatch(agentsToQuery.length);

                for (String agentName : agentsToQuery) {
                    executor.execute(() -> {
                        try {
                            String processedQuery = agentSpecificPreprocessing(resolvedQuery, agentName);
                            System.out.println("Sending to " + agentName + ": " + processedQuery);

                            ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                            request.addReceiver(new AID(agentName, AID.ISLOCALNAME));
                            request.setContent(processedQuery);
                            send(request);

                            // Wait for response with timeout
                            ACLMessage reply = blockingReceive(
                                    MessageTemplate.and(
                                            MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                                            MessageTemplate.MatchSender(new AID(agentName, AID.ISLOCALNAME))
                                    ),
                                    15000 // 15 seconds timeout
                            );

                            if (reply != null) {
                                System.out.println("Received from " + agentName + ": " + reply.getContent());
                                resultsByAgent.put(agentName, reply.getContent());
                            } else {
                                System.out.println("Timeout waiting for " + agentName);
                                agentsWithTimeout.add(agentName);
                            }
                        } catch (Exception e) {
                            System.out.println("Error with " + agentName + ": " + e.getMessage());
                            agentsWithError.add(agentName);
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                try {
                    if (!latch.await(20, TimeUnit.SECONDS)) {
                        System.out.println("Overall query timeout occurred");
                    }
                } catch (InterruptedException e) {
                    System.out.println("Query interrupted");
                } finally {
                    executor.shutdown();
                }

                // Process and combine results
                StringBuilder combinedResults = new StringBuilder();
                combinedResults.append("=== Context-Aware Combined Results ===\n\n");
                StringBuilder allValidResponses = new StringBuilder();

                for (String agentName : agentsToQuery) {
                    String result = resultsByAgent.get(agentName);
                    if (result != null) {
                        String formatted = formatResponse(agentName, result);
                        if (isValidResponse(result, agentName)) {
                            combinedResults.append(formatted).append("\n\n");
                            allValidResponses.append(formatted).append("\n\n");
                            updateContextFromResponse(result);
                            activeAgents.add(agentName); // Mark as active
                        } else {
                            System.out.println("Filtered out invalid response from " + agentName);
                        }
                    }
                }

                // Store all valid responses
                if (allValidResponses.length() > 0) {
                    KnowledgeStorage.store(resolvedQuery, allValidResponses.toString());
                }

                // Send combined response
                ACLMessage reply = originalMsg.createReply();
                reply.setPerformative(ACLMessage.INFORM);

                if (combinedResults.length() > 0) {
                    reply.setContent(combinedResults.toString());
                } else {
                    // Provide detailed error information
                    StringBuilder errorMsg = new StringBuilder("No valid results found. ");
                    if (!agentsWithTimeout.isEmpty()) {
                        errorMsg.append("Timeouts from: ").append(agentsWithTimeout).append(". ");
                    }
                    if (!agentsWithError.isEmpty()) {
                        errorMsg.append("Errors from: ").append(agentsWithError).append(". ");
                    }
                    errorMsg.append("Active agents: ").append(activeAgents);
                    reply.setContent(errorMsg.toString());
                }

                send(reply);
            }

            private String agentSpecificPreprocessing(String query, String agentName) {
//                 Special preprocessing for TogetherAgent
//                if (agentName.equals("TogetherAgent")) {
//                    System.out.println("Special preprocessing for TogetherAgent");
//                    // TogetherAgent prefers complete questions
//                    return query.endsWith("?") ? query : query + "?";
//                }

                // Simple preprocessing for Wikipedia/DuckDuckGo/Wikidata
                if (agentName.equals("WikipediaAgent") ||
                        agentName.equals("DuckDuckGoAgent") ||
                        agentName.equals("WikidataAgent")) {

                    // Remove common question prefixes and question marks
                    String processed = query.replaceAll("^(who|what|when|where|why|how|is|are|was|were|does|do|did)\\s+is\\s+", "")
                            .replaceAll("^(who|what|when|where|why|how|is|are|was|were|does|do|did)\\s+", "")
                            .replaceAll("\\?$", "")
                            .trim();

                    System.out.println("Simplified query for " + agentName + ": " + processed);
                    return processed;
                }

                // Default preprocessing for all other agents
                return query;
            }

            private void updateContextFromResponse(String response) {
                if (response.matches(".*\\b(born|age)\\b.*\\d{4}.*")) {
                    String ageInfo = response.replaceAll(".*\\b(born|age)\\b.*?(\\d{4}).*", "$1 $2");
                    if (contextMap.containsKey("current_subject")) {
                        contextMap.put(contextMap.get("current_subject") + "_age", ageInfo);
                        System.out.println("Updated context with age info: " + ageInfo);
                    }
                }
            }

            private String[] determineAgentsForQuery(String query) {
                Set<String> agents = new HashSet<>(Arrays.asList(ALL_AGENTS));

                // Special handling for TogetherAgent - only use for certain queries
                if (isQuerySuitableForTogetherAgent(query)) {
                    agents.remove("TogetherAgent");
                    System.out.println("Query not suitable for TogetherAgent");
                }

                if (isBookQuery(query)) {
                    System.out.println("books agents first");
                    for (String bookAgent : BOOK_AGENTS) {
                        if (!Arrays.asList(EXCLUDED_AGENTS).contains(bookAgent)) {
                            agents.add(bookAgent);
                        }
                    }
                }

                if (isMathQuery(query)) {
                    System.out.println("math agents first");
                    for (String mathAgent : MATH_AGENTS) {
                        if (!Arrays.asList(EXCLUDED_AGENTS).contains(mathAgent)) {
                            agents.add(mathAgent);
                        }
                    }
                }

                System.out.println("agents:::::"+agents);

                return agents.toArray(new String[0]);
            }

            private boolean isQuerySuitableForTogetherAgent(String query) {
                return false
                        ;
            }

            private boolean isBookQuery(String query) {
                String lowerQuery = query.toLowerCase();
                return lowerQuery.matches(".*\\b(book|books|author|authors|novel|novels|publish|published|publication|title|isbn|chapter|pages)\\b.*");
            }

            private boolean isMathQuery(String query) {
                String lowerQuery = query.toLowerCase();
                return lowerQuery.matches(".*\\d+\\s*[+\\-*/%^=]\\s*\\d+.*") ||
                        lowerQuery.matches(".*\\b(calculate|compute|solve|equation|math|sum of|product of|derivative|integral|algebra|geometry|trigonometry|calculus|formula|theorem)\\b.*") ||
                        lowerQuery.matches(".*\\b(sin|cos|tan|log|ln|sqrt|root|square|cube|factorial|permutation|combination)\\b.*");
            }

            private String formatResponse(String agentName, String content) {
                String sourceName = agentName.replace("Agent", "");
                return "" + sourceName + " Result\n" + content;
            }

            private boolean isValidResponse(String response, String agentName) {
                if (response == null || response.trim().isEmpty()) {
                    return false;
                }

                String lowerResponse = response.toLowerCase();

                // Agent-specific validation
                switch (agentName) {
                    case "OpenRouterAgent":
                        return !lowerResponse.contains("api error") &&
                                !lowerResponse.contains("connection error") &&
                                !lowerResponse.matches(".*\\berror\\b.*") &&
                                !lowerResponse.matches(".*\\bno (result|response)\\b.*") &&
                                response.length() > 30; // Longer minimum length for LLM responses

                    case "LangsearchAgent":
                        return !lowerResponse.contains("no results found") &&
                                !lowerResponse.contains("error fetching") &&
                                !lowerResponse.contains("api error") &&
                                response.split("\n").length >= 2 && // At least title + one result
                                response.length() > 50;

                    case "TogetherAgent":
                        return !lowerResponse.contains("i cannot answer") &&
                                !lowerResponse.contains("i don't know") &&
                                !lowerResponse.contains("no response content") &&
                                !lowerResponse.contains("api error") &&
                                !lowerResponse.contains("connection error") &&
                                response.length() > 50 &&
                                !response.matches(".*\\b(sorry|unable)\\b.*");

                    // General validation for other agents
                    default:
                        return !lowerResponse.contains("error") &&
                                !lowerResponse.contains("no result") &&
                                !lowerResponse.contains("not found") &&
                                !lowerResponse.contains("page not found") &&
                                !lowerResponse.contains("unknown source") &&
                                response.length() > 20;
                }
            }
        });
    }
}