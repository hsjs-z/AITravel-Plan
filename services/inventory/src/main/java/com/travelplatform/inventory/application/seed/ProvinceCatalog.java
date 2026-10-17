package com.travelplatform.inventory.application.seed;

import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;

import java.util.ArrayList;
import java.util.List;

public final class ProvinceCatalog {

    public static final List<String> PROVINCES = List.of(
            "北京市", "天津市", "河北省", "山西省", "内蒙古自治区", "辽宁省",
            "吉林省", "黑龙江省", "上海市", "江苏省", "浙江省", "安徽省",
            "福建省", "江西省", "山东省", "河南省", "湖北省", "湖南省",
            "广东省", "广西壮族自治区", "海南省", "重庆市", "四川省",
            "贵州省", "云南省", "西藏自治区", "陕西省", "甘肃省", "青海省",
            "宁夏回族自治区", "新疆维吾尔自治区", "香港特别行政区",
            "澳门特别行政区", "台湾省");

    private ProvinceCatalog() {
    }

    public static List<String> resolve(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return PROVINCES;
        }
        List<String> resolved = new ArrayList<>();
        for (String province : requested) {
            if (province == null || !PROVINCES.contains(province.trim())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "unknown province: " + province);
            }
            if (!resolved.contains(province.trim())) {
                resolved.add(province.trim());
            }
        }
        return List.copyOf(resolved);
    }

    public static List<List<String>> batches(List<String> provinces) {
        List<List<String>> batches = new ArrayList<>();
        for (int start = 0; start < provinces.size(); start += 6) {
            batches.add(List.copyOf(provinces.subList(start, Math.min(start + 6, provinces.size()))));
        }
        return List.copyOf(batches);
    }
}
