package com.project.service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.google.gson.*;
import io.github.cdimascio.dotenv.Dotenv;
import java.util.ArrayList;
import java.util.List;

public class LLMService {
    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    private static final String API_KEY = dotenv.get("LLM_API_KEY");
    private static final Gson gson = new Gson();
    
    private static Client client;

    // Inicializa o cliente Gemini
    private static Client getClient() {
        if (client == null) {
            if (API_KEY == null || API_KEY.isEmpty()) {
                throw new IllegalStateException("LLM_API_KEY não está definida no arquivo .env");
            }
            client = Client.builder().apiKey(API_KEY).build();
        }
        return client;
    }

    public static RelatorioLLM gerarRelatorio(RelatorioContext contexto) {
        String prompt = construirPrompt(contexto);
        
        try {
            GenerateContentResponse response = getClient().models.generateContent(
                "gemini-2.5-flash",
                prompt,
                null
            );
            
            String resposta = response.text();
            return parseResposta(resposta);
            
        } catch (Exception e) {
            throw new RuntimeException("Erro ao chamar Gemini API: " + e.getMessage(), e);
        }
    }

    public static String gerarRelatorioHTML(RelatorioContext contexto) {
        String prompt = construirPromptHTML(contexto);
        
        try {
            GenerateContentResponse response = getClient().models.generateContent(
                "gemini-2.5-flash",
                prompt,
                null
            );
            
            String html = response.text();
            html = html.replaceAll("```html\\n", "").replaceAll("```", "").trim();
            return html;
            
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar HTML: " + e.getMessage(), e);
        }
    }

    private static String construirPrompt(RelatorioContext contexto) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Você é um especialista em análise ambiental. Gere um relatório técnico em português brasileiro.\n\n");
        prompt.append("TIPO DE RELATÓRIO: ").append(contexto.tipo).append("\n\n");
        prompt.append("DADOS COLETADOS:\n");
        prompt.append(String.format("- Leituras analisadas: %d\n", contexto.numLeituras));
        prompt.append(String.format("- IQA (Índice de Qualidade do Ar): %.0f - %s\n", contexto.iqa, contexto.classificacaoIQA));
        prompt.append(String.format("- PM2.5 médio: %.1f µg/m³\n", contexto.mediaPm25));
        prompt.append(String.format("- PM10 médio: %.1f µg/m³\n", contexto.mediaPm10));
        prompt.append(String.format("- CO2 médio: %.0f ppm\n", contexto.mediaCo2));
        prompt.append(String.format("- Temperatura média: %.1f°C\n", contexto.mediaTemperatura));
        prompt.append(String.format("- Umidade média: %.1f%%\n", contexto.mediaUmidade));
        prompt.append(String.format("- Ruído médio: %.1f dB\n", contexto.mediaRuido));
        prompt.append(String.format("- Radiação UV média: %.1f\n", contexto.mediaUV));
        prompt.append(String.format("- Alertas detectados: %d\n\n", contexto.numAlertas));
        
        if (contexto.numAlertas > 0) {
            prompt.append("ALERTAS CRÍTICOS:\n");
            for (String alerta : contexto.alertasAmostra) {
                prompt.append("- ").append(alerta).append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("TAREFA:\n");
        prompt.append("1. Escreva uma CONCLUSÃO técnica e objetiva (2-3 frases) analisando os dados\n");
        prompt.append("2. Liste 3-5 RECOMENDAÇÕES práticas e específicas\n\n");
        prompt.append("FORMATO DE SAÍDA (JSON):\n");
        prompt.append("{\n");
        prompt.append("  \"conclusao\": \"texto da conclusão\",\n");
        prompt.append("  \"recomendacoes\": [\"rec 1\", \"rec 2\", \"rec 3\"]\n");
        prompt.append("}\n\n");
        prompt.append("IMPORTANTE: Retorne APENAS o JSON, sem markdown ou explicações adicionais.");
        
        return prompt.toString();
    }

    private static String construirPromptHTML(RelatorioContext contexto) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Você é um especialista em relatórios ambientais e web design.\n\n");
        prompt.append("TAREFA: Gere um relatório HTML5 completo e profissional em português brasileiro.\n\n");
        
        prompt.append("DADOS DO RELATÓRIO:\n");
        prompt.append(String.format("- Tipo: %s\n", contexto.tipo));
        prompt.append(String.format("- Leituras analisadas: %d\n", contexto.numLeituras));
        prompt.append(String.format("- IQA: %.0f (%s)\n", contexto.iqa, contexto.classificacaoIQA));
        prompt.append(String.format("- PM2.5 médio: %.1f µg/m³\n", contexto.mediaPm25));
        prompt.append(String.format("- PM10 médio: %.1f µg/m³\n", contexto.mediaPm10));
        prompt.append(String.format("- CO2 médio: %.0f ppm\n", contexto.mediaCo2));
        prompt.append(String.format("- Temperatura média: %.1f°C\n", contexto.mediaTemperatura));
        prompt.append(String.format("- Umidade média: %.1f%%\n", contexto.mediaUmidade));
        prompt.append(String.format("- Ruído médio: %.1f dB\n", contexto.mediaRuido));
        prompt.append(String.format("- Radiação UV média: %.1f\n", contexto.mediaUV));
        prompt.append(String.format("- Alertas detectados: %d\n\n", contexto.numAlertas));
        
        if (contexto.numAlertas > 0) {
            prompt.append("ALERTAS CRÍTICOS:\n");
            for (String alerta : contexto.alertasAmostra) {
                prompt.append("- ").append(alerta).append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("REQUISITOS DO HTML:\n");
        prompt.append("1. HTML5 válido (<!DOCTYPE html>, charset UTF-8, lang=\"pt-BR\")\n");
        prompt.append("2. Use Tailwind CSS via CDN: <script src=\"https://cdn.tailwindcss.com\"></script>\n");
        prompt.append("3. Design profissional tipo dashboard corporativo\n");
        prompt.append("4. Paleta de cores baseada no IQA:\n");
        prompt.append("   - IQA 0-50 (BOM): verde (bg-green-500, text-green-700)\n");
        prompt.append("   - IQA 51-100 (MODERADO): amarelo (bg-yellow-500, text-yellow-700)\n");
        prompt.append("   - IQA 101+ (RUIM): vermelho (bg-red-500, text-red-700)\n");
        prompt.append("5. Estrutura: Cabeçalho com gradiente, Grid de métricas (cards com sombras), Conclusão, Recomendações numeradas, Alertas\n");
        prompt.append("6. Responsivo (use classes Tailwind: sm:, md:, lg:)\n");
        prompt.append("7. Otimizado para impressão (use @media print no <style>)\n");
        prompt.append("8. Emojis nos títulos (🌍, 📊, 📝, 💡, ⚠️)\n");
        prompt.append("9. Título da página: 'Relatório - " + contexto.tipo + "'\n");
        prompt.append("10. Use cards com: shadow-lg, rounded-lg, p-6, bg-white\n\n");
        
        prompt.append("IMPORTANTE: Retorne APENAS o código HTML completo, sem markdown, explicações ou comentários fora do HTML.");
        
        return prompt.toString();
    }

    private static RelatorioLLM parseResposta(String resposta) {
        // Limpar markdown se houver
        resposta = resposta.replaceAll("```json\\n", "").replaceAll("```", "").trim();
        
        try {
            JsonObject json = gson.fromJson(resposta, JsonObject.class);
            
            String conclusao = json.get("conclusao").getAsString();
            
            List<String> recomendacoes = new ArrayList<>();
            JsonArray recsArray = json.getAsJsonArray("recomendacoes");
            for (int i = 0; i < recsArray.size(); i++) {
                recomendacoes.add(recsArray.get(i).getAsString());
            }
            
            return new RelatorioLLM(conclusao, recomendacoes);
            
        } catch (Exception e) {
            throw new RuntimeException("Erro ao parsear resposta da LLM: " + resposta, e);
        }
    }

    public static class RelatorioContext {
        public String tipo;
        public int numLeituras;
        public double iqa;
        public String classificacaoIQA;
        public double mediaPm25;
        public double mediaPm10;
        public double mediaCo2;
        public double mediaTemperatura;
        public double mediaUmidade;
        public double mediaRuido;
        public double mediaUV;
        public int numAlertas;
        public List<String> alertasAmostra;
    }

    public static class RelatorioLLM {
        public final String conclusao;
        public final List<String> recomendacoes;
        
        public RelatorioLLM(String conclusao, List<String> recomendacoes) {
            this.conclusao = conclusao;
            this.recomendacoes = recomendacoes;
        }
    }
}