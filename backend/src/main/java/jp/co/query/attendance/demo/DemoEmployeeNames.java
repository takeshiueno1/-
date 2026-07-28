package jp.co.query.attendance.demo;

import java.util.List;

final class DemoEmployeeNames {

    private static final List<String> NAMES = List.of(
            "上野 豪",
            "佐藤 健太",
            "鈴木 美咲",
            "高橋 翔太",
            "田中 彩",
            "伊藤 大輔",
            "渡辺 直子",
            "山本 拓也",
            "中村 由佳",
            "小林 誠",
            "加藤 愛",
            "吉田 亮",
            "山田 奈々",
            "佐々木 健",
            "山口 麻衣",
            "松本 翼",
            "井上 香織",
            "木村 悠斗",
            "林 真由",
            "斎藤 和也",
            "清水 里奈",
            "山崎 浩二",
            "森 結衣",
            "池田 修平",
            "橋本 明日香",
            "阿部 達也",
            "石川 友美",
            "山下 洋介",
            "中島 千尋",
            "前田 隆",
            "藤田 さくら",
            "後藤 優介",
            "岡田 恵",
            "長谷川 俊",
            "村上 美穂",
            "近藤 海斗",
            "石井 陽子",
            "坂本 慎一",
            "遠藤 菜摘",
            "青木 康平",
            "藤井 由美",
            "西村 和樹",
            "福田 真理",
            "太田 智也",
            "三浦 玲奈",
            "藤原 悠",
            "岡本 亜希",
            "松田 直樹",
            "中川 沙織",
            "原田 剛");

    private DemoEmployeeNames() {}

    static String get(int number) {
        if (number < 1 || number > NAMES.size()) {
            throw new IllegalArgumentException("デモ社員番号は1～50で指定してください。");
        }
        return NAMES.get(number - 1);
    }

    static int size() {
        return NAMES.size();
    }
}
