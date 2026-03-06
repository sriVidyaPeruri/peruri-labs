package com.ai.platform.api.finance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class AiInsightsService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    public Map<String, Object> generateInsights(Map<String, Object> summary) {

        try {
            String apiKey = System.getenv("ANTHROPIC_API_KEY");
            if (apiKey == null || apiKey.isBlank())
                throw new IllegalStateException("Missing ANTHROPIC_API_KEY");

            String model = System.getenv("ANTHROPIC_MODEL");
            if (model == null || model.isBlank())
                throw new IllegalStateException("Missing ANTHROPIC_MODEL");

           // System.out.println("Using Anthropic model (insights): " + model);

            String json = objectMapper.writeValueAsString(summary);
			
		//	System.out.println("json:::: " + json);

String prompt = """
You are a personal financial coach analyzing a single month's spending.

Your goal is NOT to summarize the report.
Your goal is to identify where money is going, what is controllable, and how the user could realistically save more.


When suggesting savings, quantify impact if possible (e.g., reducing dining frequency by 25% could save approximately $X per month).

Return STRICT JSON only (no markdown):

{
  "highlights": [string],
  "topSpendingCategory": string,
  "topMerchant": string,
  "concentrationNotes": [string],
  "optimizationIdeas": [string],
  "anomalies": [string]
}

CRITICAL OUTPUT RULES:

- The value of "topSpendingCategory" MUST be one of the category names present in the provided categories list.
- The value of "topMerchant" MUST be one of the merchants present in the provided merchant lists.
- Do NOT invent or infer categories that are not present in the input data.
- Categories such as "Refunds", "Transfers", "Payroll", or "Bill Payments" MUST NEVER appear in the output unless they exist in the categories list.
- If a category is not present in the categories list, it must not appear anywhere in the response.

Dataset (JSON):
""" + json + """

IMPORTANT:

- The dataset above is the ONLY source of truth.
- Only use categories that exist in the dataset.
- Only use merchants that exist in the dataset.
- Do NOT invent categories or merchants.
- If "Refunds" is not present in the dataset, it must never appear in the output.

""";

Map<String, Object> body = Map.of(
        "model", model,
        "max_tokens", 600,
        "temperature", 0,
        "system", "You are a financial data analyst. Only analyze the dataset provided. Never invent categories or merchants.",
        "messages", List.of(
                Map.of(
                        "role", "user",
                        "content", prompt
                )
        )
);

            Map<String, Object> resp = restClient.post()
                    .uri("https://api.anthropic.com/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (resp == null || !resp.containsKey("content"))
                throw new IllegalStateException("Invalid Anthropic response");

            List<?> content = (List<?>) resp.get("content");

StringBuilder sb = new StringBuilder();

for (Object obj : content) {
    Map<?, ?> part = (Map<?, ?>) obj;
    Object text = part.get("text");
    if (text != null) {
        sb.append(text.toString());
    }
}

String raw = sb.toString().trim();

            // Remove markdown fences if Claude adds them
            if (raw.startsWith("```")) {
                raw = raw.replaceAll("^```[a-zA-Z]*\\s*", "");
                raw = raw.replaceAll("\\s*```$", "");
                raw = raw.trim();
            }
//System.out.println("Claude raw response: " + raw);
            return objectMapper.readValue(
                    raw,
                    new TypeReference<>() {}
            );

        } catch (Exception e) {
            throw new RuntimeException("AI insights failed", e);
        }
    }
}