-- US8 内容资源种子数据（景点/线路/美食/文化体验，type=CONTENT）。
-- 可重复执行：所有写入均带 ON DUPLICATE KEY UPDATE；内容资源不生成库存单元、不参与交易。
-- 运行中的库需手动导入（Windows，避免 PowerShell 管道重编码）：
--   cmd /c "type deploy\seed\content-resources.sql | docker exec -i travel-mysql mysql -uroot -proot --default-character-set=utf8mb4 travel_inventory"

INSERT INTO resource (id, type, name, destination, status, attributes)
VALUES
  ('r-food-1', 'CONTENT', '过桥米线', '云南', 'ON_SHELF',
   '{"category":"FOOD","description":"云南蒙自传统米线，滚烫高汤现烫米线与配料。","tags":["米线","必吃","汤鲜"]}'),
  ('r-food-2', 'CONTENT', '汽锅鸡', '云南', 'ON_SHELF',
   '{"category":"FOOD","description":"建水紫陶汽锅蒸制的土鸡，汤清味鲜。","tags":["滇菜","滋补"]}'),
  ('r-food-3', 'CONTENT', '鲜花饼', '云南', 'ON_SHELF',
   '{"category":"FOOD","description":"以食用玫瑰入馅的云南特色酥饼。","tags":["伴手礼","玫瑰"]}'),
  ('r-culture-1', 'CONTENT', '丽江古城', '云南', 'ON_SHELF',
   '{"category":"CULTURE","description":"纳西族传统建筑与水系交织的古城，世界文化遗产。","tags":["古城","纳西","夜景"]}'),
  ('r-culture-2', 'CONTENT', '纳西古乐体验', '云南', 'ON_SHELF',
   '{"category":"CULTURE","description":"聆听由纳西族老人演奏的唐宋遗音，了解纳西东巴文化。","tags":["非遗","音乐"]}'),
  ('r-content-1', 'CONTENT', '洱海', '云南', 'ON_SHELF',
   '{"category":"ATTRACTION","description":"高原湖泊，环湖骑行与苍山洱海风光。","tags":["湖泊","骑行","日落"]}'),
  ('r-content-2', 'CONTENT', '香格里拉普达措国家公园', '云南', 'ON_SHELF',
   '{"category":"ATTRACTION","description":"高山湖泊与湿地草甸组成的国家公园。","tags":["高原","徒步"]}'),
  ('r-content-3', 'CONTENT', '滇西北经典五日线路', '云南', 'ON_SHELF',
   '{"category":"ROUTE","description":"昆明—大理—丽江—香格里拉五日串联线路。","tags":["线路","经典"]}'),
  ('r-content-4', 'CONTENT', '大理环洱海一日线路', '云南', 'ON_SHELF',
   '{"category":"ROUTE","description":"环洱海一日自驾/骑行参考线路。","tags":["线路","一日"]}'),
  ('r-food-4', 'CONTENT', '北京烤鸭', '北京', 'ON_SHELF',
   '{"category":"FOOD","description":"果木挂炉烤鸭，皮酥肉嫩，片鸭蘸酱卷饼。","tags":["必吃","烤鸭"]}'),
  ('r-food-5', 'CONTENT', '老北京炸酱面', '北京', 'ON_SHELF',
   '{"category":"FOOD","description":"黄酱肉丁配手擀面与多色面码。","tags":["面食","家常"]}'),
  ('r-culture-3', 'CONTENT', '京剧体验', '北京', 'ON_SHELF',
   '{"category":"CULTURE","description":"观看京剧折子戏，了解脸谱与唱腔。","tags":["国粹","戏楼"]}'),
  ('r-culture-4', 'CONTENT', '胡同文化漫步', '北京', 'ON_SHELF',
   '{"category":"CULTURE","description":"南锣鼓巷周边胡同导览，感受老北京四合院生活。","tags":["胡同","四合院"]}'),
  ('r-content-5', 'CONTENT', '八达岭长城', '北京', 'ON_SHELF',
   '{"category":"ATTRACTION","description":"明长城精华段，世界文化遗产。","tags":["长城","登高"]}'),
  ('r-content-6', 'CONTENT', '颐和园', '北京', 'ON_SHELF',
   '{"category":"ATTRACTION","description":"清代皇家园林，昆明湖与万寿山。","tags":["园林","皇家"]}'),
  ('r-content-7', 'CONTENT', '京城文化三日线路', '北京', 'ON_SHELF',
   '{"category":"ROUTE","description":"故宫—长城—颐和园—胡同三日文化线路。","tags":["线路","文化"]}')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  type = VALUES(type),
  destination = VALUES(destination),
  status = VALUES(status),
  attributes = VALUES(attributes);
