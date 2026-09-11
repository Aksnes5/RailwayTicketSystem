package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.Station
import com.railway.ticketsystem.model.Train
import kotlin.random.Random

object ChinaRailwayData {
    
    // 全国主要火车站数据（包含所有线路的途径车站）
    val stations = listOf(
        // 华北地区
        Station("北京南", "VNP", "北京"),
        Station("北京西", "BXP", "北京"),
        Station("北京北", "VAP", "北京"),
        Station("天津西", "TXP", "天津"),
        Station("天津南", "TIP", "天津"),
        Station("石家庄", "SJP", "石家庄"),
        Station("石家庄北", "VVP", "石家庄"),
        Station("太原南", "TNV", "太原"),
        Station("呼和浩特东", "NDC", "呼和浩特"),
        Station("包头", "BTC", "包头"),
        
        // 东北地区
        Station("沈阳北", "SBT", "沈阳"),
        Station("沈阳南", "SOT", "沈阳"),
        Station("大连北", "DFT", "大连"),
        Station("长春西", "CRT", "长春"),
        Station("哈尔滨西", "VAB", "哈尔滨"),
        Station("齐齐哈尔南", "QNB", "齐齐哈尔"),
        
        // 华东地区
        Station("上海虹桥", "AOH", "上海"),
        Station("上海南", "SNH", "上海"),
        Station("南京南", "NKH", "南京"),
        Station("苏州北", "OHH", "苏州"),
        Station("无锡东", "WGH", "无锡"),
        Station("常州北", "ESH", "常州"),
        Station("镇江", "ZJH", "镇江"),
        Station("杭州东", "HGH", "杭州"),
        Station("杭州南", "HNH", "杭州"),
        Station("宁波", "NGH", "宁波"),
        Station("温州南", "VRH", "温州"),
        Station("合肥南", "ENH", "合肥"),
        Station("福州", "FZS", "福州"),
        Station("厦门北", "XKS", "厦门"),
        Station("南昌西", "NXG", "南昌"),
        Station("济南西", "JGK", "济南"),
        Station("青岛北", "QHK", "青岛"),
        Station("烟台南", "YLK", "烟台"),
        
        // 从线路中提取的车站
        // 宁蓉铁路
        Station("南京南", "Nanjing South", "南京"),
        Station("全椒", "Quanjiao", "全椒"),
        Station("肥东", "Feidong", "肥东"),
        Station("合肥南", "Hefei South", "合肥"),
        Station("六安", "Lu'an", "六安"),
        Station("金寨", "Jinzhai", "金寨"),
        Station("麻城北", "Macheng North", "麻城"),
        Station("红安西", "Hong'an West", "红安"),
        Station("汉口", "Hankou", "武汉"),
        Station("汉川", "Hanchuan", "汉川"),
        Station("天门南", "Tianmen South", "天门"),
        Station("仙桃西", "Xiantao West", "仙桃"),
        Station("潜江", "Qianjiang", "潜江"),
        Station("荆州", "Jingzhou", "荆州"),
        Station("枝江北", "Zhijiang North", "枝江"),
        Station("宜昌东", "Yichang East", "宜昌"),
        Station("野三关", "Yesanguan", "野三关"),
        Station("高坪", "Gaoping", "高坪"),
        Station("建始", "Jianshi", "建始"),
        Station("恩施", "Enshi", "恩施"),
        Station("利川", "Lichuan", "利川"),
        Station("石柱县", "Shizhu County", "石柱"),
        Station("丰都", "Fengdu", "丰都"),
        Station("涪陵北", "Fuling North", "涪陵"),
        Station("长寿北", "Changshou North", "长寿"),
        Station("重庆北", "Chongqing North", "重庆"),
        Station("合川", "Hechuan", "合川"),
        Station("潼南", "Tongnan", "潼南"),
        Station("遂宁", "Suining", "遂宁"),
        Station("大英东", "Daying East", "大英"),
        Station("成都东", "Chengdu East", "成都"),
        
        // 京港高铁
        Station("北京丰台", "Beijing Fengtai", "北京"),
        Station("雄安", "Xiong'an", "雄安"),
        Station("任丘西", "Renqiu West", "任丘"),
        Station("肃宁东", "Suning East", "肃宁"),
        Station("深州东", "Shenzhou East", "深州"),
        Station("衡水南", "Hengshui South", "衡水"),
        Station("枣强南", "Zaoqiang South", "枣强"),
        Station("清河西", "Qinghe West", "清河"),
        Station("临清东", "Linqing East", "临清"),
        Station("聊城西", "Liaocheng West", "聊城"),
        Station("台前东", "Taiqian East", "台前"),
        Station("梁山", "Liangshan", "梁山"),
        Station("郓城", "Yuncheng", "郓城"),
        Station("菏泽东", "Heze East", "菏泽"),
        Station("曹县西", "Caoxian West", "曹县"),
        Station("商丘", "Shangqiu", "商丘"),
        Station("商丘东", "Shangqiu East", "商丘"),
        Station("芦庙", "Lumiao", "芦庙"),
        Station("亳州南", "Bozhou South", "亳州"),
        Station("古城东", "Gucheng East", "古城"),
        Station("太和东", "Taihe East", "太和"),
        Station("阜阳西", "Fuyang West", "阜阳"),
        Station("颍上北", "Yingshang North", "颍上"),
        Station("凤台南", "Fengtai South", "凤台"),
        Station("寿县", "Shouxian", "寿县"),
        Station("淮南南", "Huainan South", "淮南"),
        Station("水家湖", "Shuijiahu", "水家湖"),
        Station("合肥北城", "Hefei North City", "合肥"),
        Station("合肥西", "Hefei West", "合肥"),
        Station("肥西", "Feixi", "肥西"),
        Station("舒城东", "Shucheng East", "舒城"),
        Station("庐江西", "Lujiang West", "庐江"),
        Station("桐城东", "Tongcheng East", "桐城"),
        Station("桐城南", "Tongcheng South", "桐城"),
        Station("安庆西", "Anqing West", "安庆"),
        Station("潜山南", "Qianshan South", "潜山"),
        Station("太湖南", "Taihu South", "太湖"),
        Station("宿松东", "Susong East", "宿松"),
        Station("黄梅东", "Huangmei East", "黄梅"),
        Station("孔垄北", "Konglong North", "孔垄"),
        Station("庐山", "Lushan", "庐山"),
        Station("庐山南", "Lushan South", "庐山"),
        Station("共青城东", "Gongqingcheng East", "共青城"),
        Station("昌北机场", "Changbei Airport", "南昌"),
        Station("南昌东", "Nanchang East", "南昌"),
        Station("丰城东", "Fengcheng East", "丰城"),
        Station("樟树东", "Zhangshu East", "樟树"),
        Station("新干东", "Xingan East", "新干"),
        Station("峡江", "Xiajiang", "峡江"),
        Station("吉水西", "Jishui West", "吉水"),
        Station("吉安西", "Ji'an West", "吉安"),
        Station("泰和", "Taihe", "泰和"),
        Station("万安县", "Wan'an County", "万安"),
        Station("兴国西", "Xingguo West", "兴国"),
        Station("赣县北", "Ganxian North", "赣县"),
        Station("赣州西", "Ganzhou West", "赣州"),
        Station("信丰西", "Xinfeng West", "信丰"),
        Station("龙南东", "Longnan East", "龙南"),
        Station("定南西", "Dingnan West", "定南"),
        Station("和平东", "Heping East", "和平"),
        Station("龙川西", "Longchuan West", "龙川"),
        Station("东源", "Dongyuan", "东源"),
        Station("河源东", "Heyuan East", "河源"),
        Station("博罗北", "Boluo North", "博罗"),
        Station("惠州北", "Huizhou North", "惠州"),
        Station("仲恺", "Zhongkai", "仲恺"),
        Station("塘厦", "Tangxia", "塘厦"),
        Station("光明城", "Guangmingcheng", "光明城"),
        Station("深圳北", "Shenzhen North", "深圳"),
        Station("福田", "Futian", "福田"),
        Station("香港西九龙", "Hong Kong West Kowloon", "香港"),
        
        // 杭昌高铁
        Station("杭州南", "HZN", "杭州"),
        Station("富阳", "FYH", "杭州"),
        Station("桐庐", "TLH", "桐庐"),
        Station("建德", "JDH", "建德"),
        Station("千岛湖", "QDH", "淳安"),
        Station("三阳", "SYH", "三阳"),
        Station("绩溪北", "JXH", "绩溪"),
        Station("歙县北", "SXH", "歙县"),
        Station("黄山北", "HSH", "黄山"),
        Station("黟县东", "YXD", "黟县"),
        Station("祁门南", "QMN", "祁门"),
        Station("浮梁东", "FLD", "浮梁"),
        Station("景德镇北", "JDB", "景德镇"),
        Station("乐平北", "LPB", "乐平"),
        Station("鄱阳", "PYN", "鄱阳"),
        Station("余干", "YGG", "余干"),
        Station("进贤北", "JXB", "进贤"),
        Station("南昌东", "NCE", "南昌"),

        // 宁安高铁
        Station("江宁西", "JNW", "南京"),
        Station("当涂东", "DTD", "当涂"),
        Station("芜湖南", "WUN", "芜湖"),
        Station("繁昌西", "FCX", "繁昌"),
        Station("铜陵", "TLN", "铜陵"),
        Station("池州", "CZI", "池州"),
        Station("安庆", "AQH", "安庆"),

        // 安九高铁
        Station("潜山", "QSH", "潜山"),
        Station("黄梅南", "HMN", "黄梅"),

        // 宣绩高铁
        Station("宣城", "XCH", "宣城"),
        Station("宁国南", "NGN", "宁国"),

        // 昌福铁路
        Station("南昌西", "Nanchang West", "南昌"),
        Station("抚州", "Fuzhou", "抚州"),
        Station("南城", "Nancheng", "南城"),
        Station("南丰", "Nanfeng", "南丰"),
        Station("建宁县北", "Jianning County North", "建宁"),
        Station("泰宁", "Taining", "泰宁"),
        Station("将乐", "Jiangle", "将乐"),
        Station("三明北", "Sanming North", "三明"),
        Station("尤溪", "Youxi", "尤溪"),
        Station("永泰", "Yongtai", "永泰"),
        Station("福州", "Fuzhou", "福州"),
        
        // 厦深铁路
        Station("厦门北", "Xiamen North", "厦门"),
        Station("角美", "Jiaomei", "角美"),
        Station("漳州", "Zhangzhou", "漳州"),
        Station("漳浦", "Zhangpu", "漳浦"),
        Station("云霄", "Yunxiao", "云霄"),
        Station("诏安", "Zhao'an", "诏安"),
        Station("饶平", "Raoping", "饶平"),
        Station("潮汕", "Chaoshan", "潮汕"),
        Station("潮阳", "Chaoyang", "潮阳"),
        Station("普宁", "Puning", "普宁"),
        Station("葵潭", "Kuitan", "葵潭"),
        Station("陆丰", "Lufeng", "陆丰"),
        Station("汕尾", "Shanwei", "汕尾"),
        Station("鲘门", "Houmen", "鲘门"),
        Station("惠东", "Huidong", "惠东"),
        Station("惠州南", "Huizhou South", "惠州"),
        Station("深圳坪山", "Shenzhen Pingshan", "深圳"),
        Station("深圳北", "Shenzhen North", "深圳"),
        
        // 沪宁城际铁路
        Station("南京", "Nanjing", "南京"),
        Station("仙林", "Xianlin", "南京"),
        Station("宝华山", "Baohuashan", "宝华山"),
        Station("镇江", "Zhenjiang", "镇江"),
        Station("丹徒", "Dantu", "丹徒"),
        Station("丹阳", "Danyang", "丹阳"),
        Station("常州", "Changzhou", "常州"),
        Station("戚墅堰", "Qishuyan", "戚墅堰"),
        Station("惠山", "Huishan", "惠山"),
        Station("无锡", "Wuxi", "无锡"),
        Station("无锡新区", "Wuxi New District", "无锡"),
        Station("苏州新区", "Suzhou New District", "苏州"),
        Station("苏州", "Suzhou", "苏州"),
        Station("苏州园区", "Suzhou Industrial Park", "苏州"),
        Station("阳澄湖", "Yangchenghu", "阳澄湖"),
        Station("昆山南", "Kunshan South", "昆山"),
        Station("花桥", "Huaqiao", "花桥"),
        Station("安亭北", "Anting North", "安亭"),
        Station("南翔北", "Nanxiang North", "南翔"),
        Station("上海西", "Shanghai West", "上海"),
        Station("上海", "Shanghai", "上海"),
        
        // 武宜高铁
        Station("汉川北", "Hanchuan North", "汉川"),
        Station("天门", "Tianmen", "天门"),
        Station("京山南", "Jingshan South", "京山"),
        Station("钟祥南", "Zhongxiang South", "钟祥"),
        Station("荆门西", "Jingmen West", "荆门"),
        Station("当阳西", "Dangyang West", "当阳"),
        Station("宜昌北", "Yichang North", "宜昌"),
        
        // 武九客专
        Station("葛店南", "Gedian South", "鄂州"),
        Station("华容南", "Huarong South", "华容"),
        Station("鄂州", "Ezhou", "鄂州"),
        Station("鄂州东", "Ezhou East", "鄂州"),
        Station("花湖", "Huahu", "鄂州"),
        Station("黄石北", "Huangshi North", "黄石"),
        Station("大冶北", "Daye North", "大冶"),
        Station("白沙铺", "Baishapu", "阳新"),
        Station("阳新", "Yangxin", "阳新"),
        Station("枫林", "Fenglin", "阳新"),
        Station("瑞昌西", "Ruichang West", "瑞昌"),
        Station("柴桑", "Chaisang", "九江"),
        Station("庐山", "Lushan", "九江"),
        Station("九江", "Jiujiang", "九江"),
        
        // 昌九城际
        Station("南昌", "Nanchang", "南昌"),
        Station("共青城", "Gongqingcheng", "共青城"),
        Station("德安", "De'an", "德安"),
        Station("永修", "Yongxiu", "永修"),
        
        // 汉十高铁
        Station("孝感东", "Xiaogan East", "孝感"),
        Station("云梦东", "Yunmeng East", "云梦"),
        Station("安陆西", "Anlu West", "安陆"),
        Station("随州南", "Suizhou South", "随州"),
        Station("随县", "Suixian", "随县"),
        Station("枣阳", "Zaoyang", "枣阳"),
        Station("襄阳东", "Xiangyang East", "襄阳"),
        Station("隆中", "Longzhong", "襄阳"),
        Station("谷城北", "Gucheng North", "谷城"),
        Station("丹江口南", "Danjiangkou South", "丹江口"),
        Station("武当山西", "Wudangshan West", "武当山"),
        Station("十堰东", "Shiyan East", "十堰"),
        
        // 武广高铁
        Station("武汉", "Wuhan", "武汉"),
        Station("咸宁北", "Xianning North", "咸宁"),
        Station("赤壁北", "Chibi North", "赤壁"),
        Station("岳阳东", "Yueyang East", "岳阳"),
        Station("汨罗东", "Miluo East", "汨罗"),
        Station("长沙南", "Changsha South", "长沙"),
        Station("株洲西", "Zhuzhou West", "株洲"),
        Station("衡阳东", "Hengyang East", "衡阳"),
        Station("郴州西", "Chenzhou West", "郴州"),
        Station("韶关", "Shaoguan", "韶关"),
        Station("清远", "Qingyuan", "清远"),
        Station("广州南", "Guangzhou South", "广州"),
        
        // 京沪高铁
        Station("沧州西", "Cangzhou West", "沧州"),
        Station("德州东", "Dezhou East", "德州"),
        Station("泰安", "Tai'an", "泰安"),
        Station("曲阜东", "Qufu East", "曲阜"),
        Station("滕州东", "Tengzhou East", "滕州"),
        Station("枣庄", "Zaozhuang", "枣庄"),
        Station("徐州东", "Xuzhou East", "徐州"),
        Station("宿州东", "Suzhou East", "宿州"),
        Station("蚌埠南", "Bengbu South", "蚌埠"),
        Station("定远", "Dingyuan", "定远"),
        Station("滁州", "Chuzhou", "滁州"),
        Station("镇江南", "Zhenjiang South", "镇江"),
        Station("丹阳北", "Danyang North", "丹阳"),
        Station("昆山南", "Kunshan South", "昆山"),
        
        // 成渝高铁
        Station("成都东", "Chengdu East", "成都"),
        Station("简阳南", "Jianyang South", "简阳"),
        Station("资阳北", "Ziyang North", "资阳"),
        Station("资中北", "Zizhong North", "资中"),
        Station("内江北", "Neijiang North", "内江"),
        Station("隆昌北", "Longchang North", "隆昌"),
        Station("荣昌北", "Rongchang North", "荣昌"),
        Station("大足南", "Dazu South", "大足"),
        Station("永川东", "Yongchuan East", "永川"),
        Station("璧山", "Bishan", "璧山"),
        Station("重庆北", "Chongqing North", "重庆"),
        
        // 广深港高铁
        Station("庆盛", "Qingsheng", "庆盛"),
        Station("虎门", "Humen", "虎门"),
        Station("光明城", "Guangmingcheng", "光明城"),
        Station("深圳北", "Shenzhen North", "深圳"),
        Station("福田", "Futian", "福田"),
        Station("香港西九龙", "Hong Kong West Kowloon", "香港"),
        
        // 京广高铁
        Station("涿州东", "Zhuozhou East", "涿州"),
        Station("高碑店东", "Gaobeidian East", "高碑店"),
        Station("保定东", "Baoding East", "保定"),
        Station("定州东", "Dingzhou East", "定州"),
        Station("正定机场", "Zhengding Airport", "正定"),
        Station("高邑西", "Gaoyi West", "高邑"),
        Station("邢台东", "Xingtai East", "邢台"),
        Station("邯郸东", "Handan East", "邯郸"),
        Station("安阳东", "Anyang East", "安阳"),
        Station("鹤壁东", "Hebi East", "鹤壁"),
        Station("新乡东", "Xinxiang East", "新乡"),
        Station("郑州东", "Zhengzhou East", "郑州"),
        Station("许昌东", "Xuchang East", "许昌"),
        Station("漯河西", "Luohe West", "漯河"),
        Station("驻马店西", "Zhumadian West", "驻马店"),
        Station("明港东", "Minggang East", "明港"),
        Station("信阳东", "Xinyang East", "信阳"),
        Station("孝感北", "Xiaogan North", "孝感"),
        
        // 沪昆高铁
        Station("松江南", "Songjiang South", "松江"),
        Station("金山北", "Jinshan North", "金山"),
        Station("嘉善南", "Jiashan South", "嘉善"),
        Station("嘉兴南", "Jiaxing South", "嘉兴"),
        Station("桐乡", "Tongxiang", "桐乡"),
        Station("海宁西", "Haining West", "海宁"),
        Station("余杭", "Yuhang", "余杭"),
        Station("诸暨", "Zhuji", "诸暨"),
        Station("义乌", "Yiwu", "义乌"),
        Station("金华", "Jinhua", "金华"),
        Station("龙游", "Longyou", "龙游"),
        Station("衢州", "Quzhou", "衢州"),
        Station("江山", "Jiangshan", "江山"),
        Station("玉山南", "Yushan South", "玉山"),
        Station("上饶", "Shangrao", "上饶"),
        Station("弋阳", "Yiyang", "弋阳"),
        Station("鹰潭北", "Yingtan North", "鹰潭"),
        Station("进贤南", "Jinxian South", "进贤"),
        Station("高安", "Gao'an", "高安"),
        Station("新余北", "Xinyu North", "新余"),
        Station("宜春", "Yichun", "宜春"),
        Station("萍乡北", "Pingxiang North", "萍乡"),
        Station("醴陵东", "Liling East", "醴陵"),
        Station("湘潭北", "Xiangtan North", "湘潭"),
        Station("韶山南", "Shaoshan South", "韶山"),
        Station("娄底南", "Loudi South", "娄底"),
        Station("邵阳北", "Shaoyang North", "邵阳"),
        Station("新化南", "Xinhua South", "新化"),
        Station("溆浦南", "Xupu South", "溆浦"),
        Station("怀化南", "Huaihua South", "怀化"),
        Station("芷江", "Zhijiang", "芷江"),
        Station("新晃西", "Xinhuang West", "新晃"),
        Station("铜仁南", "Tongren South", "铜仁"),
        Station("三穗", "Sansui", "三穗"),
        Station("凯里南", "Kaili South", "凯里"),
        Station("贵定北", "Guiding North", "贵定"),
        Station("贵阳东", "Guiyang East", "贵阳"),
        Station("贵阳北", "Guiyang North", "贵阳"),
        Station("平坝南", "Pingba South", "平坝"),
        Station("安顺西", "Anshun West", "安顺"),
        Station("关岭", "Guanling", "关岭"),
        Station("普安县", "Pu'an County", "普安"),
        Station("盘州", "Panzhou", "盘州"),
        Station("富源北", "Fuyuan North", "富源"),
        Station("曲靖北", "Qujing North", "曲靖"),
        Station("嵩明", "Songming", "嵩明"),
        Station("昆明南", "Kunming South", "昆明"),
        
        // 哈大高铁
        Station("双城北", "Shuangcheng North", "双城"),
        Station("扶余北", "Fuyu North", "扶余"),
        Station("德惠西", "Dehui West", "德惠"),
        Station("公主岭南", "Gongzhuling South", "公主岭"),
        Station("四平东", "Siping East", "四平"),
        Station("昌图西", "Changtu West", "昌图"),
        Station("开原西", "Kaiyuan West", "开原"),
        Station("铁岭西", "Tieling West", "铁岭"),
        Station("沈阳", "Shenyang", "沈阳"),
        Station("辽阳", "Liaoyang", "辽阳"),
        Station("鞍山西", "Anshan West", "鞍山"),
        Station("海城西", "Haicheng West", "海城"),
        Station("营口东", "Yingkou East", "营口"),
        Station("盖州西", "Gaizhou West", "盖州"),
        Station("鲅鱼圈", "Bayuquan", "鲅鱼圈"),
        Station("瓦房店西", "Wafangdian West", "瓦房店"),
        Station("普湾", "Puwan", "普湾"),
        
        // 西成高铁
        Station("西安北", "Xi'an North", "西安"),
        Station("阿房宫", "Epang Palace", "西安"),
        Station("鄠邑", "Huyi", "鄠邑"),
        Station("佛坪", "Foping", "佛坪"),
        Station("洋县西", "Yangxian West", "洋县"),
        Station("城固北", "Chenggu North", "城固"),
        Station("汉中", "Hanzhong", "汉中"),
        Station("新集", "Xinji", "新集"),
        Station("宁强南", "Ningqiang South", "宁强"),
        Station("朝天", "Chaotian", "朝天"),
        Station("广元", "Guangyuan", "广元"),
        Station("剑门关", "Jianmenguan", "剑门关"),
        Station("青川", "Qingchuan", "青川"),
        Station("江油北", "Jiangyou North", "江油"),
        Station("江油", "Jiangyou", "江油"),
        Station("青莲", "Qinglian", "青莲"),
        Station("绵阳", "Mianyang", "绵阳"),
        Station("罗江东", "Luodong East", "罗江"),
        Station("德阳", "Deyang", "德阳"),
        Station("广汉北", "Guanghan North", "广汉"),
        Station("青白江东", "Qingbaijiang East", "青白江"),
        Station("新都东", "Xindu East", "新都"),
        
        // 兰新高铁
        Station("兰州西", "Lanzhou West", "兰州"),
        Station("西固", "Xigu", "西固"),
        Station("永登", "Yongdeng", "永登"),
        Station("天祝", "Tianzhu", "天祝"),
        Station("古浪", "Gulang", "古浪"),
        Station("武威", "Wuwei", "武威"),
        Station("金昌", "Jinchang", "金昌"),
        Station("山丹", "Shandan", "山丹"),
        Station("张掖西", "Zhangye West", "张掖"),
        Station("临泽南", "Linze South", "临泽"),
        Station("高台南", "Gaotai South", "高台"),
        Station("酒泉南", "Jiuquan South", "酒泉"),
        Station("嘉峪关南", "Jiayuguan South", "嘉峪关"),
        Station("清泉", "Qingquan", "清泉"),
        Station("玉门", "Yumen", "玉门"),
        Station("柳园南", "Liuyuan South", "柳园"),
        Station("哈密", "Hami", "哈密"),
        Station("吐哈", "Tuha", "吐哈"),
        Station("鄯善北", "Shanshan North", "鄯善"),
        Station("吐鲁番北", "Turpan North", "吐鲁番"),
        Station("乌鲁木齐", "Urumqi", "乌鲁木齐"),
        
        // 郑西高铁
        Station("郑州西", "Zhengzhou West", "郑州"),
        Station("巩义南", "Gongyi South", "巩义"),
        Station("洛阳龙门", "Luoyang Longmen", "洛阳"),
        Station("渑池南", "Mianchi South", "渑池"),
        Station("三门峡南", "Sanmenxia South", "三门峡"),
        Station("灵宝西", "Lingbao West", "灵宝"),
        Station("华山北", "Huashan North", "华山"),
        Station("渭南北", "Weinan North", "渭南"),
        
        // 合福高铁
        Station("长临河", "Changlinhe", "长临河"),
        Station("巢湖东", "Chaohu East", "巢湖"),
        Station("无为", "Wuwei", "无为"),
        Station("铜陵北", "Tongling North", "铜陵"),
        Station("南陵", "Nanling", "南陵"),
        Station("泾县", "Jingxian", "泾县"),
        Station("旌德", "Jingde", "旌德"),
        Station("绩溪北", "Jixi North", "绩溪"),
        Station("歙县北", "Shexian North", "歙县"),
        Station("黄山北", "Huangshan North", "黄山"),
        Station("婺源", "Wuyuan", "婺源"),
        Station("德兴", "Dexing", "德兴"),
        Station("五府山", "Wufushan", "五府山"),
        Station("武夷山北", "Wuyishan North", "武夷山"),
        Station("武夷山东", "Wuyishan East", "武夷山"),
        Station("建瓯西", "Jian'ou West", "建瓯"),
        Station("南平北", "Nanping North", "南平"),
        Station("古田北", "Gutian North", "古田"),
        Station("闽清北", "Minqing North", "闽清"),
        
        // 贵广高铁
        Station("龙洞堡", "Longdongbao", "龙洞堡"),
        Station("龙里北", "Longli North", "龙里"),
        Station("贵定县", "Guiding County", "贵定"),
        Station("都匀东", "Duyun East", "都匀"),
        Station("三都县", "Sandu County", "三都"),
        Station("榕江", "Rongjiang", "榕江"),
        Station("从江", "Congjiang", "从江"),
        Station("三江南", "Sanjiang South", "三江"),
        Station("五通", "Wutong", "五通"),
        Station("桂林西", "Guilin West", "桂林"),
        Station("桂林北", "Guilin North", "桂林"),
        Station("永福南", "Yongfu South", "永福"),
        Station("鹿寨北", "Luzhai North", "鹿寨"),
        Station("柳州", "Liuzhou", "柳州"),
        Station("进德", "Jinde", "进德"),
        Station("来宾北", "Laibin North", "来宾"),
        Station("宾阳", "Binyang", "宾阳"),
        Station("五塘", "Wutang", "五塘"),
        Station("南宁东", "Nanning East", "南宁"),
        Station("南宁", "Nanning", "南宁"),
        Station("南宁西", "Nanning West", "南宁"),
        Station("隆安东", "Long'an East", "隆安"),
        Station("平果", "Pingguo", "平果"),
        Station("田东北", "Tiandong North", "田东"),
        Station("田阳", "Tianyang", "田阳"),
        Station("百色", "Baise", "百色"),
        Station("田林", "Tianlin", "田林"),
        Station("隆林", "Longlin", "隆林"),
        Station("册亨", "Ceheng", "册亨"),
        Station("安龙", "Anlong", "安龙"),
        Station("兴义", "Xingyi", "兴义"),
        
        // 华中地区
        Station("武汉", "WHN", "武汉"),
        Station("汉口", "HKN", "武汉"),
        Station("武昌", "WCN", "武汉"),
        Station("郑州东", "ZAF", "郑州"),
        Station("长沙南", "CWQ", "长沙"),
        Station("株洲西", "ZAQ", "株洲"),
        Station("衡阳东", "HVQ", "衡阳"),
        Station("岳阳东", "YIQ", "岳阳"),
        
        // 华南地区
        Station("广州南", "IZQ", "广州"),
        Station("广州东", "GGQ", "广州"),
        Station("深圳北", "IOQ", "深圳"),
        Station("珠海", "ZHQ", "珠海"),
        Station("中山北", "ZGQ", "中山"),
        Station("江门", "JWQ", "江门"),
        Station("湛江西", "ZWQ", "湛江"),
        Station("南宁东", "NFZ", "南宁"),
        Station("柳州", "LZZ", "柳州"),
        Station("桂林北", "GBZ", "桂林"),
        Station("海口东", "HMQ", "海口"),
        Station("三亚", "SEQ", "三亚"),
        
        // 西南地区
        Station("成都东", "ICW", "成都"),
        Station("成都南", "CNW", "成都"),
        Station("重庆北", "CUW", "重庆"),
        Station("重庆西", "CXW", "重庆"),
        Station("贵阳北", "KQW", "贵阳"),
        Station("昆明南", "KOM", "昆明"),
        Station("大理", "DKM", "大理"),
        Station("丽江", "LHM", "丽江"),
        Station("拉萨", "LSO", "拉萨"),
        
        // 西北地区
        Station("西安北", "EAY", "西安"),
        Station("兰州西", "LAJ", "兰州"),
        Station("西宁", "XNO", "西宁"),
        Station("银川", "YIJ", "银川"),
        Station("乌鲁木齐", "WAR", "乌鲁木齐"),
        
        // 湖北地区
        Station("襄阳东", "XYE", "襄阳"),
        Station("宜城", "YC", "宜城"),
        Station("汉川", "HCN", "汉川"),
        Station("汉川北", "HBN", "汉川"),
        Station("天门", "TMN", "天门"),
        Station("天门南", "TNN", "天门"),
        Station("京山南", "JSN", "京山"),
        Station("钟祥南", "ZSN", "钟祥"),
        Station("荆门西", "JMN", "荆门"),
        Station("沙洋西", "SYW", "沙洋"),
        Station("仙桃西", "XAN", "仙桃"),
        Station("潜江", "QJN", "潜江"),
        Station("荆州", "JBN", "荆州"),
        Station("宜昌北", "YBN", "宜昌"),
        Station("宜昌东", "HAN", "宜昌"),
        
        // 其他重要车站
        Station("徐州东", "UUH", "徐州"),
        Station("蚌埠南", "BMH", "蚌埠"),
        Station("滁州", "CXH", "滁州"),
        Station("马鞍山东", "OMH", "马鞍山"),
        Station("芜湖", "WHH", "芜湖"),
        Station("宣城", "ECH", "宣城"),
        Station("湖州", "VZH", "湖州"),
        Station("嘉兴南", "EPH", "嘉兴"),
        Station("绍兴北", "SLH", "绍兴"),
        Station("台州", "TZH", "台州"),
        Station("金华", "JBH", "金华"),
        Station("衢州", "QEH", "衢州"),
        Station("丽水", "USH", "丽水"),
        Station("温州", "RZH", "温州"),
        Station("瑞安", "RAH", "瑞安"),
        Station("苍南", "CEH", "苍南"),
        Station("平阳", "ARH", "平阳"),
        Station("鳌江", "ARH", "鳌江"),
        Station("福鼎", "FES", "福鼎"),
        Station("太姥山", "TLS", "太姥山"),
        Station("霞浦", "XOS", "霞浦"),
        Station("宁德", "NES", "宁德"),
        Station("连江", "LKS", "连江"),
        Station("福州南", "FYS", "福州"),
        Station("福清", "FQS", "福清"),
        Station("莆田", "PTS", "莆田"),
        Station("仙游", "XMS", "仙游"),
        Station("惠安", "HNS", "惠安"),
        Station("泉州", "QYS", "泉州"),
        Station("晋江", "JJS", "晋江"),
        Station("厦门", "XMS", "厦门"),
        Station("漳州", "ZUS", "漳州"),
        Station("龙岩", "LYS", "龙岩"),
        Station("三明北", "SHS", "三明"),
        Station("南平北", "NBS", "南平"),
        Station("建瓯西", "JUS", "建瓯"),
        Station("武夷山东", "WCS", "武夷山"),
        Station("上饶", "SRG", "上饶"),
        Station("鹰潭北", "YKG", "鹰潭"),
        Station("抚州东", "FDG", "抚州"),
        Station("进贤南", "JUG", "进贤"),
        Station("南昌", "NCG", "南昌"),
        Station("永修", "ACG", "永修"),
        Station("德安", "DAG", "德安"),
        Station("庐山", "LSG", "庐山"),
        Station("九江", "JJG", "九江"),
        Station("瑞昌西", "RXG", "瑞昌"),
        Station("阳新", "YOG", "阳新"),
        Station("大冶北", "DBN", "大冶"),
        Station("黄石北", "KSN", "黄石"),
        Station("鄂州", "ECN", "鄂州"),
        
        // 徐兰高铁
        Station("徐州东", "UUH", "徐州"),
        Station("徐州", "XCH", "徐州"),
        Station("萧县北", "XBN", "萧县"),
        Station("永城北", "YCN", "永城"),
        Station("砀山南", "DSN", "砀山"),
        Station("商丘", "SQF", "商丘"),
        Station("民权北", "MQN", "民权"),
        Station("兰考南", "LKN", "兰考"),
        Station("开封北", "KFN", "开封"),
        Station("郑州东", "ZAF", "郑州"),
        Station("郑州西", "ZAF", "郑州"),
        Station("巩义南", "GYF", "巩义"),
        Station("洛阳龙门", "LYF", "洛阳"),
        Station("渑池南", "MCF", "渑池"),
        Station("三门峡南", "SMF", "三门峡"),
        Station("灵宝西", "LBF", "灵宝"),
        Station("华山北", "HSF", "华山"),
        Station("渭南北", "WNF", "渭南"),
        Station("西安北", "EAY", "西安"),
        Station("咸阳秦都", "XAY", "咸阳"),
        Station("杨陵南", "YLY", "杨陵"),
        Station("岐山", "QSH", "岐山"),
        Station("宝鸡南", "BJY", "宝鸡"),
        Station("东岔", "DCY", "东岔"),
        Station("天水南", "TSY", "天水"),
        Station("秦安", "QAY", "秦安"),
        Station("通渭", "TWY", "通渭"),
        Station("定西北", "DXY", "定西"),
        Station("榆中", "YZY", "榆中"),
        Station("兰州西", "LAJ", "兰州")
    ).plus(LatestRailwayNetwork.stations).distinctBy { it.name }
    
    // 生成随机车次数据
    fun generateRandomTrains(): List<Train> {
        val trainList = mutableListOf<Train>()
        val trainNumbers = listOf("G", "D", "C", "K", "T", "Z")
        val timeSlots = (6..22).toList()
        
        // 为每个车站对生成随机车次
        stations.forEach { fromStation ->
            stations.forEach { toStation ->
                if (fromStation != toStation) {
                    // 随机决定是否生成车次（30%概率）
                    if (Random.nextFloat() < 0.3f) {
                        val trainType = trainNumbers.random()
                        val trainNumber = "$trainType${Random.nextInt(1, 9999)}"
                        val departureHour = timeSlots.random()
                        val departureMinute = Random.nextInt(0, 60)
                        val departureTime = String.format("%02d:%02d", departureHour, departureMinute)

                        // 使用站数×(15..25)与价格=站数×(40-同因子)的一致逻辑
                        val variants = buildVariants(fromStation.name, toStation.name)
                        val (durationStr, price) = variants.random()
                        val arrivalTime = calculateArrivalTime(departureTime, durationStr)
                        val availableSeats = Random.nextInt(10, 100)
                        
                        trainList.add(
                            Train(
                                trainNumber,
                                fromStation.name,
                                toStation.name,
                                departureTime,
                                arrivalTime,
                                durationStr,
                                price,
                                availableSeats,
                                emptyList() // 普通车次没有途径车站
                            )
                        )
                    }
                }
            }
        }
        
        return trainList
    }
    
    // 简化的价格计算（基于车站名称的哈希值）
    private fun calculateArrivalTime(departureTime: String, duration: String): String {
        val parts = departureTime.split(":")
        val depMinutes = parts[0].toInt() * 60 + parts[1].toInt()
        val durMinutes = parseDurationToMinutes(duration)
        val total = depMinutes + durMinutes
        val hour = (total / 60) % 24
        val minute = total % 60
        return String.format("%02d:%02d", hour, minute)
    }

    private fun parseDurationToMinutes(duration: String): Int {
        return if (duration.contains("小时")) {
            val h = duration.substringBefore("小时").toIntOrNull() ?: 0
            val m = duration.substringAfter("小时").replace("分钟", "").trim().toIntOrNull() ?: 0
            h * 60 + m
        } else duration.replace("分钟", "").trim().toIntOrNull() ?: 0
    }

    private fun buildVariants(from: String, to: String): List<Pair<String, Double>> {
        val pathStations = com.railway.ticketsystem.model.RailwayRouteManager.getRouteStationsMinStops(from, to)
        val segments = (pathStations.size - 1).coerceAtLeast(1)
        val base = baseFactorForPair(from, to)
        val candidates = listOf(base - 4, base - 2, base, base + 2, base + 4)
            .map { it.coerceIn(15, 25) }
        val unique = LinkedHashSet<Int>(candidates)
        var f = 15
        while (unique.size < 5) {
            if (!unique.contains(f)) unique.add(f)
            f++
            if (f > 25) f = 15
        }
        return unique.map { factor ->
            val minutes = segments * factor
            val duration = if (minutes >= 60) "${minutes / 60}小时${minutes % 60}分钟" else "${minutes}分钟"
            val price = (segments * (40 - factor)).coerceAtLeast(0).toDouble()
            duration to price
        }
    }

    private fun baseFactorForPair(from: String, to: String): Int {
        val key = if (from <= to) "$from->$to" else "$to->$from"
        val hash = key.hashCode()
        val range = 11
        val offset = kotlin.math.abs(hash % range)
        return 15 + offset
    }
    
    // 获取所有车次（禁用初始随机车次，避免与统一规则冲突）
    val trains: List<Train> by lazy { emptyList() }
    
    // 根据路线查找车次
    fun getTrainsByRoute(departure: String, arrival: String): List<Train> {
        return trains.filter { 
            it.departureStation == departure && it.arrivalStation == arrival 
        }
    }
    
    // 根据车站名查找车站
    fun getStationByName(name: String): Station? {
        return stations.find { it.name == name }
    }
    
    // 搜索车站
    fun searchStations(query: String): List<Station> {
        if (query.isEmpty()) return stations
        
        return stations.filter { station ->
            station.name.contains(query, ignoreCase = true) ||
            station.code.contains(query, ignoreCase = true) ||
            station.city.contains(query, ignoreCase = true)
        }
    }
}
