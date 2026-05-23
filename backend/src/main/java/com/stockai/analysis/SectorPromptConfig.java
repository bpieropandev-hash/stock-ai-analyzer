package com.stockai.analysis;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SectorPromptConfig {

    private static final Map<SectorType, String> INSTRUCTIONS = Map.of(
            SectorType.ENERGIA,
            "Selic tem impacto moderado. Brent e câmbio são determinantes. " +
            "Em ano eleitoral, risco político deve penalizar gestaoRisco em 1-2 pontos.",

            SectorType.FINANCEIRO,
            "Selic alta beneficia margens de intermediação. Inadimplência é o principal risco. " +
            "Dividendos são estruturais nesse setor — yield abaixo de 5% merece desconto.",

            SectorType.VAREJO,
            "Selic alta é o maior inimigo — comprime consumo e eleva custo da dívida. " +
            "Sem lucro consistente, retornoAcionista não pode passar de 4. " +
            "Use P/VPA quando o P/L estiver distorcido por resultado negativo.",

            SectorType.MINERACAO,
            "Preço das commodities e câmbio dominam o resultado. " +
            "Produção (toneladas) e custo C1 por tonelada são os fundamentos mais importantes. " +
            "Alta do dólar amplifica receita mas também o custo da dívida externa.",

            SectorType.BEBIDAS,
            "Setor defensivo com margens estáveis e previsíveis. " +
            "Dividend yield consistente é esperado pelo mercado. " +
            "Volume de vendas por canal e poder de precificação são os drivers de resultado.",

            SectorType.INDUSTRIA,
            "Exportações beneficiadas pelo câmbio elevado. " +
            "Eficiência operacional e investimento em inovação determinam competitividade de longo prazo. " +
            "ROE acima de 15% é referência de qualidade para empresas industriais.",

            SectorType.LOGISTICA,
            "Crescimento do e-commerce sustenta demanda estrutural. " +
            "Custo de combustível e alavancagem financeira são os principais riscos. " +
            "EBITDA e margem EBITDA são as métricas mais relevantes para comparação setorial.",

            SectorType.PAPEL_CELULOSE,
            "Commodity global cotada em dólar — câmbio favorável amplifica resultados em BRL. " +
            "Custo de madeira própria e capacidade instalada são os fundamentos de longo prazo. " +
            "Ciclos de preço de celulose (oferta global) dominam a volatilidade de curto prazo.",

            SectorType.OUTROS,
            "Analise com base exclusivamente nos fundamentos disponíveis. " +
            "Sem contexto setorial específico — aplique critérios gerais de qualidade."
    );

    public String getInstructions(SectorType sector) {
        return INSTRUCTIONS.getOrDefault(sector, INSTRUCTIONS.get(SectorType.OUTROS));
    }
}
