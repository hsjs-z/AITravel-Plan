package com.travelplatform.inventory.infrastructure;

import com.travelplatform.inventory.application.seed.SeedDtos;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeepSeekScenicSpotClient {

    private static final String SYSTEM_PROMPT = """
            你是中国旅游景区数据整理员。只输出一个合法 JSON 对象，不要 Markdown，不要解释。
            结构必须为：
            {
              "provinces": [
                {
                  "province": "省级行政区全称",
                  "attractions": [
                    {
                      "name": "景区名称",
                      "city": "城市",
                      "level": "5A或4A或3A或2A或1A，未知时为null",
                      "honorTitle": "核心称号，最多10个中文字符，未知时为null",
                      "summary": "一句话简介，最多120字",
                      "suggestedHours": 4.0,
                      "ticketPrice": 100
                    }
                  ]
                }
              ]
            }
            每个省必须给出6个热门景区。不要编造 A 级评级或称号；不知道就填 null。
            不要编造省份；只处理用户列出的省份。
            """;

    private final ChatClient chatClient;
    private final boolean configured;

    public DeepSeekScenicSpotClient(ChatClient.Builder builder,
                                    @Value("${seed.deepseek-api-key:}") String apiKey) {
        this.chatClient = builder.build();
        this.configured = apiKey != null && !apiKey.isBlank();
    }

    public boolean isConfigured() {
        return configured;
    }

    public SeedDtos.SeedBatchResponse generate(List<String> provinces) {
        String prompt = "请为以下省级行政区各生成6个热门景区：" + String.join("、", provinces);
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(prompt)
                .call()
                .entity(SeedDtos.SeedBatchResponse.class);
    }
}
