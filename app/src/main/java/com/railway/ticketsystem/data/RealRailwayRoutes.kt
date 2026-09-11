package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.RailwayRoute
import com.railway.ticketsystem.model.RouteType
import com.railway.ticketsystem.model.Station
import com.railway.ticketsystem.model.StationNetwork
import com.railway.ticketsystem.model.RailwayRouteManager

/**
 * 真实铁路线路数据
 * 基于中国实际铁路网络创建
 */
object RealRailwayRoutes {
    
    @Volatile
    private var installed = false

    /**
     * 初始化所有真实铁路线路。可以重复调用，线路只装一次。
     *
     * 原来没有这层保护：重复调用会把同一批线路再 addRoute 一遍，图上的边数直接翻倍，
     * 所有 BFS 跟着变慢——实测边数从 1270 涨到 3560。RailwayData.preloadData 和
     * RailwayGraphManager 都会调到这里，所以必须幂等。
     */
    fun initializeRoutes() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            installAll()
            installed = true
        }
    }

    private fun installAll() {
        // 宁蓉铁路（南京南-成都东）
        val ningrongRoute = RailwayRoute(
            routeId = "NINGRONG001",
            routeName = "宁蓉铁路",
            stations = listOf(
                Station("南京南", "Nanjing South", "NJN"),
                Station("全椒", "Quanjiao", "QJ"),
                Station("肥东", "Feidong", "FD"),
                Station("合肥南", "Hefei South", "HFN"),
                Station("六安", "Lu'an", "LA"),
                Station("金寨", "Jinzhai", "JZ"),
                Station("麻城北", "Macheng North", "MCN"),
                Station("红安西", "Hong'an West", "HAW"),
                Station("汉口", "Hankou", "HK"),
                Station("汉川", "Hanchuan", "HCN"),
                Station("天门南", "Tianmen South", "TNN"),
                Station("仙桃西", "Xiantao West", "XTW"),
                Station("潜江", "Qianjiang", "QJ"),
                Station("荆州", "Jingzhou", "JZ"),
                Station("枝江北", "Zhijiang North", "ZJN"),
                Station("宜昌东", "Yichang East", "YCD"),
                Station("野三关", "Yesanguan", "YSG"),
                Station("高坪", "Gaoping", "GP"),
                Station("建始", "Jianshi", "JS"),
                Station("恩施", "Enshi", "ES"),
                Station("利川", "Lichuan", "LC"),
                Station("石柱县", "Shizhu County", "SZC"),
                Station("丰都", "Fengdu", "FD"),
                Station("涪陵北", "Fuling North", "FLN"),
                Station("长寿北", "Changshou North", "CSN"),
                Station("重庆北", "Chongqing North", "CQN"),
                Station("合川", "Hechuan", "HC"),
                Station("潼南", "Tongnan", "TN"),
                Station("遂宁", "Suining", "SN"),
                Station("大英东", "Daying East", "DYD"),
                Station("成都东", "Chengdu East", "CDE")
            ),
            segmentPrices = mapOf(
                "南京南-全椒" to 15.0,
                "全椒-肥东" to 20.0,
                "肥东-合肥南" to 12.0,
                "合肥南-六安" to 25.0,
                "六安-金寨" to 18.0,
                "金寨-麻城北" to 35.0,
                "麻城北-红安西" to 15.0,
                "红安西-汉口" to 20.0,
                "汉口-汉川" to 12.0,
                "汉川-天门南" to 8.0,
                "天门南-仙桃西" to 6.0,
                "仙桃西-潜江" to 10.0,
                "潜江-荆州" to 15.0,
                "荆州-枝江北" to 12.0,
                "枝江北-宜昌东" to 18.0,
                "宜昌东-野三关" to 25.0,
                "野三关-高坪" to 15.0,
                "高坪-建始" to 12.0,
                "建始-恩施" to 18.0,
                "恩施-利川" to 20.0,
                "利川-石柱县" to 22.0,
                "石柱县-丰都" to 15.0,
                "丰都-涪陵北" to 18.0,
                "涪陵北-长寿北" to 12.0,
                "长寿北-重庆北" to 15.0,
                "重庆北-合川" to 18.0,
                "合川-潼南" to 15.0,
                "潼南-遂宁" to 20.0,
                "遂宁-大英东" to 12.0,
                "大英东-成都东" to 18.0
            ),
            totalPrice = 580.0,
            totalDuration = "12小时30分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(ningrongRoute)
        
        // 京港高铁（北京丰台-香港西九龙）
        val jinggangRoute = RailwayRoute(
            routeId = "JINGGANG001",
            routeName = "京港高铁",
            stations = listOf(
                Station("北京丰台", "Beijing Fengtai", "BFT"),
                Station("雄安", "Xiong'an", "XA"),
                Station("任丘西", "Renqiu West", "RQW"),
                Station("肃宁东", "Suning East", "SNE"),
                Station("深州东", "Shenzhou East", "SZE"),
                Station("衡水南", "Hengshui South", "HSS"),
                Station("枣强南", "Zaoqiang South", "ZQS"),
                Station("清河西", "Qinghe West", "QHW"),
                Station("临清东", "Linqing East", "LQE"),
                Station("聊城西", "Liaocheng West", "LCW"),
                Station("台前东", "Taiqian East", "TQE"),
                Station("梁山", "Liangshan", "LS"),
                Station("郓城", "Yuncheng", "YC"),
                Station("菏泽东", "Heze East", "HZE"),
                Station("曹县西", "Caoxian West", "CXW"),
                Station("商丘", "Shangqiu", "SQ"),
                Station("商丘东", "Shangqiu East", "SQE"),
                Station("芦庙", "Lumiao", "LM"),
                Station("亳州南", "Bozhou South", "BZS"),
                Station("古城东", "Gucheng East", "GCE"),
                Station("太和东", "Taihe East", "THE"),
                Station("阜阳西", "Fuyang West", "FYW"),
                Station("颍上北", "Yingshang North", "YSN"),
                Station("凤台南", "Fengtai South", "FTS"),
                Station("寿县", "Shouxian", "SX"),
                Station("淮南南", "Huainan South", "HNS"),
                Station("水家湖", "Shuijiahu", "SJH"),
                Station("合肥北城", "Hefei North City", "HBNC"),
                Station("合肥西", "Hefei West", "HFW"),
                Station("肥西", "Feixi", "FX"),
                Station("舒城东", "Shucheng East", "SCE"),
                Station("庐江西", "Lujiang West", "LJW"),
                Station("桐城东", "Tongcheng East", "TCE"),
                Station("桐城南", "Tongcheng South", "TCS"),
                Station("安庆西", "Anqing West", "AQW"),
                Station("潜山南", "Qianshan South", "QSN"),
                Station("太湖南", "Taihu South", "THS"),
                Station("宿松东", "Susong East", "SSE"),
                Station("黄梅东", "Huangmei East", "HME"),
                Station("孔垄北", "Konglong North", "KLN"),
                Station("庐山", "Lushan", "LS"),
                Station("庐山南", "Lushan South", "LSS"),
                Station("共青城东", "Gongqingcheng East", "GQCE"),
                Station("昌北机场", "Changbei Airport", "CBA"),
                Station("南昌东", "Nanchang East", "NCE"),
                Station("丰城东", "Fengcheng East", "FCE"),
                Station("樟树东", "Zhangshu East", "ZSE"),
                Station("新干东", "Xingan East", "XGE"),
                Station("峡江", "Xiajiang", "XJ"),
                Station("吉水西", "Jishui West", "JSW"),
                Station("吉安西", "Ji'an West", "JAW"),
                Station("泰和", "Taihe", "TH"),
                Station("万安县", "Wan'an County", "WAC"),
                Station("兴国西", "Xingguo West", "XGW"),
                Station("赣县北", "Ganxian North", "GXN"),
                Station("赣州西", "Ganzhou West", "GZW"),
                Station("信丰西", "Xinfeng West", "XFW"),
                Station("龙南东", "Longnan East", "LNE"),
                Station("定南西", "Dingnan West", "DNW"),
                Station("和平东", "Heping East", "HPE"),
                Station("龙川西", "Longchuan West", "LCW"),
                Station("东源", "Dongyuan", "DY"),
                Station("河源东", "Heyuan East", "HYE"),
                Station("博罗北", "Boluo North", "BLN"),
                Station("惠州北", "Huizhou North", "HZN"),
                Station("仲恺", "Zhongkai", "ZK"),
                Station("塘厦", "Tangxia", "TX"),
                Station("光明城", "Guangmingcheng", "GMC"),
                Station("深圳北", "Shenzhen North", "SZN"),
                Station("福田", "Futian", "FT"),
                Station("香港西九龙", "Hong Kong West Kowloon", "HKW")
            ),
            segmentPrices = mapOf(
                "北京丰台-雄安" to 25.0,
                "雄安-任丘西" to 15.0,
                "任丘西-肃宁东" to 12.0,
                "肃宁东-深州东" to 18.0,
                "深州东-衡水南" to 20.0,
                "衡水南-枣强南" to 15.0,
                "枣强南-清河西" to 18.0,
                "清河西-临清东" to 15.0,
                "临清东-聊城西" to 20.0,
                "聊城西-台前东" to 18.0,
                "台前东-梁山" to 15.0,
                "梁山-郓城" to 12.0,
                "郓城-菏泽东" to 15.0,
                "菏泽东-曹县西" to 12.0,
                "曹县西-商丘" to 18.0,
                "商丘-商丘东" to 8.0,
                "商丘东-芦庙" to 15.0,
                "芦庙-亳州南" to 12.0,
                "亳州南-古城东" to 15.0,
                "古城东-太和东" to 18.0,
                "太和东-阜阳西" to 20.0,
                "阜阳西-颍上北" to 15.0,
                "颍上北-凤台南" to 18.0,
                "凤台南-寿县" to 12.0,
                "寿县-淮南南" to 15.0,
                "淮南南-水家湖" to 12.0,
                "水家湖-合肥北城" to 18.0,
                "合肥北城-合肥西" to 15.0,
                "合肥西-肥西" to 12.0,
                "肥西-舒城东" to 15.0,
                "舒城东-庐江西" to 18.0,
                "庐江西-桐城东" to 15.0,
                "桐城东-桐城南" to 12.0,
                "桐城南-安庆西" to 18.0,
                "安庆西-潜山南" to 15.0,
                "潜山南-太湖南" to 12.0,
                "太湖南-宿松东" to 15.0,
                "宿松东-黄梅东" to 18.0,
                "黄梅东-孔垄北" to 12.0,
                "孔垄北-庐山" to 15.0,
                "庐山-庐山南" to 8.0,
                "庐山南-共青城东" to 18.0,
                "共青城东-昌北机场" to 20.0,
                "昌北机场-南昌东" to 15.0,
                "南昌东-丰城东" to 18.0,
                "丰城东-樟树东" to 15.0,
                "樟树东-新干东" to 18.0,
                "新干东-峡江" to 12.0,
                "峡江-吉水西" to 15.0,
                "吉水西-吉安西" to 18.0,
                "吉安西-泰和" to 15.0,
                "泰和-万安县" to 18.0,
                "万安县-兴国西" to 20.0,
                "兴国西-赣县北" to 15.0,
                "赣县北-赣州西" to 18.0,
                "赣州西-信丰西" to 20.0,
                "信丰西-龙南东" to 18.0,
                "龙南东-定南西" to 15.0,
                "定南西-和平东" to 18.0,
                "和平东-龙川西" to 20.0,
                "龙川西-东源" to 15.0,
                "东源-河源东" to 12.0,
                "河源东-博罗北" to 18.0,
                "博罗北-惠州北" to 20.0,
                "惠州北-仲恺" to 15.0,
                "仲恺-塘厦" to 18.0,
                "塘厦-光明城" to 20.0,
                "光明城-深圳北" to 15.0,
                "深圳北-福田" to 10.0,
                "福田-香港西九龙" to 30.0
            ),
            totalPrice = 850.0,
            totalDuration = "8小时30分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(jinggangRoute)
        
        // 宣绩高铁（芜湖-绩溪北）
        val xuanjiRoute = RailwayRoute(
            routeId = "XUANJI001",
            routeName = "宣绩高铁",
            stations = listOf(
                Station("芜湖", "Wuhu", "WHH"),
                Station("宣城", "Xuancheng", "XCH"),
                Station("宁国南", "Ningguo South", "NGN"),
                Station("绩溪北", "Jixi North", "JXH")
            ),
            segmentPrices = mapOf(
                "芜湖-宣城" to 20.0,
                "宣城-宁国南" to 18.0,
                "宁国南-绩溪北" to 16.0
            ),
            totalPrice = 54.0,
            totalDuration = "45分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(xuanjiRoute)

        // 杭昌高铁（杭州南-南昌东）
        val hangchangRoute = RailwayRoute(
            routeId = "HANGCHANG001",
            routeName = "杭昌高铁",
            stations = listOf(
                Station("杭州南", "Hangzhou South", "HZN"),
                Station("富阳", "Fuyang", "FYH"),
                Station("桐庐", "Tonglu", "TLH"),
                Station("建德", "Jiande", "JDH"),
                Station("千岛湖", "Qiandaohu", "QDH"),
                Station("三阳", "Sanyang", "SYH"),
                Station("绩溪北", "Jixi North", "JXH"),
                Station("歙县北", "Shexian North", "SXH"),
                Station("黄山北", "Huangshan North", "HSH"),
                Station("黟县东", "Yixian East", "YXD"),
                Station("祁门南", "Qimen South", "QMN"),
                Station("浮梁东", "Fuliang East", "FLD"),
                Station("景德镇北", "Jingdezhen North", "JDZ"),
                Station("乐平北", "Leping North", "LPB"),
                Station("鄱阳", "Poyang", "PYN"),
                Station("余干", "Yugan", "YGN"),
                Station("进贤北", "Jinxian North", "JXB"),
                Station("南昌东", "Nanchang East", "NCE")
            ),
            segmentPrices = mapOf(
                "杭州南-富阳" to 12.0,
                "富阳-桐庐" to 10.0,
                "桐庐-建德" to 15.0,
                "建德-千岛湖" to 18.0,
                "千岛湖-三阳" to 14.0,
                "三阳-绩溪北" to 16.0,
                "绩溪北-歙县北" to 12.0,
                "歙县北-黄山北" to 10.0,
                "黄山北-黟县东" to 14.0,
                "黟县东-祁门南" to 12.0,
                "祁门南-浮梁东" to 15.0,
                "浮梁东-景德镇北" to 10.0,
                "景德镇北-乐平北" to 12.0,
                "乐平北-鄱阳" to 14.0,
                "鄱阳-余干" to 12.0,
                "余干-进贤北" to 16.0,
                "进贤北-南昌东" to 18.0
            ),
            totalPrice = 226.0,
            totalDuration = "2小时45分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(hangchangRoute)

        // 宁安高铁（南京南-安庆）
        val ninganRoute = RailwayRoute(
            routeId = "NINGAN001",
            routeName = "宁安高铁",
            stations = listOf(
                Station("南京南", "Nanjing South", "NJN"),
                Station("江宁西", "Jiangning West", "JNW"),
                Station("马鞍山东", "Ma'anshan East", "OMH"),
                Station("当涂东", "Dangtu East", "DTD"),
                Station("芜湖", "Wuhu", "WHH"),
                Station("芜湖南", "Wuhu South", "WUN"),
                Station("繁昌西", "Fanchang West", "FCX"),
                Station("铜陵", "Tongling", "TLN"),
                Station("池州", "Chizhou", "CZI"),
                Station("安庆", "Anqing", "AQH")
            ),
            segmentPrices = mapOf(
                "南京南-江宁西" to 10.0,
                "江宁西-马鞍山东" to 18.0,
                "马鞍山东-当涂东" to 12.0,
                "当涂东-芜湖" to 15.0,
                "芜湖-芜湖南" to 6.0,
                "芜湖南-繁昌西" to 10.0,
                "繁昌西-铜陵" to 18.0,
                "铜陵-池州" to 20.0,
                "池州-安庆" to 22.0
            ),
            totalPrice = 131.0,
            totalDuration = "1小时30分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(ninganRoute)

        // 安九高铁（安庆西-庐山）
        val anjiuRoute = RailwayRoute(
            routeId = "ANJIU001",
            routeName = "安九高铁",
            stations = listOf(
                Station("安庆西", "Anqing West", "AQW"),
                Station("潜山", "Qianshan", "QSH"),
                Station("太湖南", "Taihu South", "THS"),
                Station("宿松东", "Susong East", "SSE"),
                Station("黄梅东", "Huangmei East", "HME"),
                Station("黄梅南", "Huangmei South", "HMN"),
                Station("庐山", "Lushan", "LS" )
            ),
            segmentPrices = mapOf(
                "安庆西-潜山" to 18.0,
                "潜山-太湖南" to 16.0,
                "太湖南-宿松东" to 12.0,
                "宿松东-黄梅东" to 14.0,
                "黄梅东-黄梅南" to 8.0,
                "黄梅南-庐山" to 20.0
            ),
            totalPrice = 88.0,
            totalDuration = "1小时10分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(anjiuRoute)

        // 昌福铁路（南昌西-福州）
        val changfuRoute = RailwayRoute(
            routeId = "CHANGFU001",
            routeName = "昌福铁路",
            stations = listOf(
                Station("南昌西", "Nanchang West", "NCW"),
                Station("抚州", "Fuzhou", "FZ"),
                Station("南城", "Nancheng", "NC"),
                Station("南丰", "Nanfeng", "NF"),
                Station("建宁县北", "Jianning County North", "JNN"),
                Station("泰宁", "Taining", "TN"),
                Station("将乐", "Jiangle", "JL"),
                Station("三明北", "Sanming North", "SMN"),
                Station("尤溪", "Youxi", "YX"),
                Station("永泰", "Yongtai", "YT"),
                Station("福州", "Fuzhou", "FZS")
            ),
            segmentPrices = mapOf(
                "南昌西-抚州" to 25.0,
                "抚州-南城" to 20.0,
                "南城-南丰" to 15.0,
                "南丰-建宁县北" to 18.0,
                "建宁县北-泰宁" to 12.0,
                "泰宁-将乐" to 15.0,
                "将乐-三明北" to 18.0,
                "三明北-尤溪" to 15.0,
                "尤溪-永泰" to 20.0,
                "永泰-福州" to 25.0
            ),
            totalPrice = 183.0,
            totalDuration = "3小时15分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(changfuRoute)
        
        // 厦深铁路（厦门北-深圳北）
        val xiashenRoute = RailwayRoute(
            routeId = "XIASHEN001",
            routeName = "厦深铁路",
            stations = listOf(
                Station("厦门北", "Xiamen North", "XMN"),
                Station("角美", "Jiaomei", "JM"),
                Station("漳州", "Zhangzhou", "ZZ"),
                Station("漳浦", "Zhangpu", "ZP"),
                Station("云霄", "Yunxiao", "YX"),
                Station("诏安", "Zhao'an", "ZA"),
                Station("饶平", "Raoping", "RP"),
                Station("潮汕", "Chaoshan", "CS"),
                Station("潮阳", "Chaoyang", "CY"),
                Station("普宁", "Puning", "PN"),
                Station("葵潭", "Kuitan", "KT"),
                Station("陆丰", "Lufeng", "LF"),
                Station("汕尾", "Shanwei", "SW"),
                Station("鲘门", "Houmen", "HM"),
                Station("惠东", "Huidong", "HD"),
                Station("惠州南", "Huizhou South", "HZS"),
                Station("深圳坪山", "Shenzhen Pingshan", "SZP"),
                Station("深圳北", "Shenzhen North", "SZN")
            ),
            segmentPrices = mapOf(
                "厦门北-角美" to 8.0,
                "角美-漳州" to 12.0,
                "漳州-漳浦" to 15.0,
                "漳浦-云霄" to 12.0,
                "云霄-诏安" to 10.0,
                "诏安-饶平" to 8.0,
                "饶平-潮汕" to 15.0,
                "潮汕-潮阳" to 12.0,
                "潮阳-普宁" to 15.0,
                "普宁-葵潭" to 10.0,
                "葵潭-陆丰" to 12.0,
                "陆丰-汕尾" to 15.0,
                "汕尾-鲘门" to 8.0,
                "鲘门-惠东" to 12.0,
                "惠东-惠州南" to 15.0,
                "惠州南-深圳坪山" to 18.0,
                "深圳坪山-深圳北" to 12.0
            ),
            totalPrice = 220.0,
            totalDuration = "3小时45分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(xiashenRoute)
        
        // 沪宁城际铁路（南京-上海）
        val huningRoute = RailwayRoute(
            routeId = "HUNING001",
            routeName = "沪宁城际铁路",
            stations = listOf(
                Station("南京", "Nanjing", "NJ"),
                Station("仙林", "Xianlin", "XL"),
                Station("宝华山", "Baohuashan", "BHS"),
                Station("镇江", "Zhenjiang", "ZJ"),
                Station("丹徒", "Dantu", "DT"),
                Station("丹阳", "Danyang", "DY"),
                Station("常州", "Changzhou", "CZ"),
                Station("戚墅堰", "Qishuyan", "QSY"),
                Station("惠山", "Huishan", "HS"),
                Station("无锡", "Wuxi", "WX"),
                Station("无锡新区", "Wuxi New District", "WXN"),
                Station("苏州新区", "Suzhou New District", "SZN"),
                Station("苏州", "Suzhou", "SZ"),
                Station("苏州园区", "Suzhou Industrial Park", "SZI"),
                Station("阳澄湖", "Yangchenghu", "YCH"),
                Station("昆山南", "Kunshan South", "KSN"),
                Station("花桥", "Huaqiao", "HQ"),
                Station("安亭北", "Anting North", "ATN"),
                Station("南翔北", "Nanxiang North", "NXN"),
                Station("上海西", "Shanghai West", "SHW"),
                Station("上海", "Shanghai", "SH")
            ),
            segmentPrices = mapOf(
                "南京-仙林" to 8.0,
                "仙林-宝华山" to 6.0,
                "宝华山-镇江" to 12.0,
                "镇江-丹徒" to 8.0,
                "丹徒-丹阳" to 10.0,
                "丹阳-常州" to 15.0,
                "常州-戚墅堰" to 8.0,
                "戚墅堰-惠山" to 12.0,
                "惠山-无锡" to 10.0,
                "无锡-无锡新区" to 8.0,
                "无锡新区-苏州新区" to 15.0,
                "苏州新区-苏州" to 8.0,
                "苏州-苏州园区" to 6.0,
                "苏州园区-阳澄湖" to 8.0,
                "阳澄湖-昆山南" to 10.0,
                "昆山南-花桥" to 8.0,
                "花桥-安亭北" to 6.0,
                "安亭北-南翔北" to 8.0,
                "南翔北-上海西" to 10.0,
                "上海西-上海" to 6.0
            ),
            totalPrice = 192.0,
            totalDuration = "1小时30分",
            routeType = RouteType.INTERCITY
        )
        RailwayRouteManager.addRoute(huningRoute)
        
        // 武广高铁（武汉-广州南）
        val wuguangRoute = RailwayRoute(
            routeId = "WUGUANG001",
            routeName = "武广高铁",
            stations = listOf(
                Station("武汉", "Wuhan", "WH"),
                Station("咸宁北", "Xianning North", "XNN"),
                Station("赤壁北", "Chibi North", "CBN"),
                Station("岳阳东", "Yueyang East", "YYD"),
                Station("汨罗东", "Miluo East", "MLD"),
                Station("长沙南", "Changsha South", "CSN"),
                Station("株洲西", "Zhuzhou West", "ZZW"),
                Station("衡阳东", "Hengyang East", "HYD"),
                Station("郴州西", "Chenzhou West", "CZW"),
                Station("韶关", "Shaoguan", "SG"),
                Station("清远", "Qingyuan", "QY"),
                Station("广州南", "Guangzhou South", "GZN")
            ),
            segmentPrices = mapOf(
                "武汉-咸宁北" to 25.0,
                "咸宁北-赤壁北" to 15.0,
                "赤壁北-岳阳东" to 35.0,
                "岳阳东-汨罗东" to 20.0,
                "汨罗东-长沙南" to 25.0,
                "长沙南-株洲西" to 15.0,
                "株洲西-衡阳东" to 45.0,
                "衡阳东-郴州西" to 35.0,
                "郴州西-韶关" to 40.0,
                "韶关-清远" to 30.0,
                "清远-广州南" to 25.0
            ),
            totalPrice = 310.0,
            totalDuration = "4小时15分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(wuguangRoute)
        
        // 京沪高铁（北京南-上海虹桥）
        val jinghuRoute = RailwayRoute(
            routeId = "JINGHU001",
            routeName = "京沪高铁",
            stations = listOf(
                Station("北京南", "Beijing South", "BJN"),
                Station("天津南", "Tianjin South", "TJN"),
                Station("沧州西", "Cangzhou West", "CZW"),
                Station("德州东", "Dezhou East", "DZD"),
                Station("济南西", "Jinan West", "JNW"),
                Station("泰安", "Tai'an", "TA"),
                Station("曲阜东", "Qufu East", "QFD"),
                Station("滕州东", "Tengzhou East", "TZD"),
                Station("枣庄", "Zaozhuang", "ZZ"),
                Station("徐州东", "Xuzhou East", "XZD"),
                Station("宿州东", "Suzhou East", "SZD"),
                Station("蚌埠南", "Bengbu South", "BBN"),
                Station("定远", "Dingyuan", "DY"),
                Station("滁州", "Chuzhou", "CZ"),
                Station("南京南", "Nanjing South", "NJN"),
                Station("镇江南", "Zhenjiang South", "ZJN"),
                Station("丹阳北", "Danyang North", "DYB"),
                Station("常州北", "Changzhou North", "CZN"),
                Station("无锡东", "Wuxi East", "WXD"),
                Station("苏州北", "Suzhou North", "SZN"),
                Station("昆山南", "Kunshan South", "KSN"),
                Station("上海虹桥", "Shanghai Hongqiao", "SHH")
            ),
            segmentPrices = mapOf(
                "北京南-天津南" to 55.0,
                "天津南-沧州西" to 35.0,
                "沧州西-德州东" to 25.0,
                "德州东-济南西" to 45.0,
                "济南西-泰安" to 20.0,
                "泰安-曲阜东" to 25.0,
                "曲阜东-滕州东" to 15.0,
                "滕州东-枣庄" to 10.0,
                "枣庄-徐州东" to 20.0,
                "徐州东-宿州东" to 15.0,
                "宿州东-蚌埠南" to 25.0,
                "蚌埠南-定远" to 15.0,
                "定远-滁州" to 20.0,
                "滁州-南京南" to 25.0,
                "南京南-镇江南" to 20.0,
                "镇江南-丹阳北" to 15.0,
                "丹阳北-常州北" to 15.0,
                "常州北-无锡东" to 20.0,
                "无锡东-苏州北" to 15.0,
                "苏州北-昆山南" to 10.0,
                "昆山南-上海虹桥" to 15.0
            ),
            totalPrice = 553.0,
            totalDuration = "4小时28分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(jinghuRoute)
        
        // 成渝高铁（成都东-重庆北）
        val chengyuRoute = RailwayRoute(
            routeId = "CHENGYU001",
            routeName = "成渝高铁",
            stations = listOf(
                Station("成都东", "Chengdu East", "CDE"),
                Station("简阳南", "Jianyang South", "JYN"),
                Station("资阳北", "Ziyang North", "ZYB"),
                Station("资中北", "Zizhong North", "ZZB"),
                Station("内江北", "Neijiang North", "NJB"),
                Station("隆昌北", "Longchang North", "LCB"),
                Station("荣昌北", "Rongchang North", "RCB"),
                Station("大足南", "Dazu South", "DZN"),
                Station("永川东", "Yongchuan East", "YCE"),
                Station("璧山", "Bishan", "BS"),
                Station("重庆北", "Chongqing North", "CQN")
            ),
            segmentPrices = mapOf(
                "成都东-简阳南" to 20.0,
                "简阳南-资阳北" to 15.0,
                "资阳北-资中北" to 18.0,
                "资中北-内江北" to 12.0,
                "内江北-隆昌北" to 15.0,
                "隆昌北-荣昌北" to 10.0,
                "荣昌北-大足南" to 12.0,
                "大足南-永川东" to 15.0,
                "永川东-璧山" to 18.0,
                "璧山-重庆北" to 20.0
            ),
            totalPrice = 155.0,
            totalDuration = "1小时25分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(chengyuRoute)
        
        // 广深港高铁（广州南-香港西九龙）
        val guangshenRoute = RailwayRoute(
            routeId = "GUANGSHEN001",
            routeName = "广深港高铁",
            stations = listOf(
                Station("广州南", "Guangzhou South", "GZN"),
                Station("庆盛", "Qingsheng", "QS"),
                Station("虎门", "Humen", "HM"),
                Station("光明城", "Guangmingcheng", "GMC"),
                Station("深圳北", "Shenzhen North", "SZN"),
                Station("福田", "Futian", "FT"),
                Station("香港西九龙", "Hong Kong West Kowloon", "HKW")
            ),
            segmentPrices = mapOf(
                "广州南-庆盛" to 15.0,
                "庆盛-虎门" to 20.0,
                "虎门-光明城" to 25.0,
                "光明城-深圳北" to 15.0,
                "深圳北-福田" to 10.0,
                "福田-香港西九龙" to 30.0
            ),
            totalPrice = 115.0,
            totalDuration = "48分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(guangshenRoute)
        
        // 京广高铁（北京西-广州南）
        val jingguangRoute = RailwayRoute(
            routeId = "JINGUANG001",
            routeName = "京广高铁",
            stations = listOf(
                Station("北京西", "Beijing West", "BXP"),
                Station("涿州东", "Zhuozhou East", "ZDP"),
                Station("高碑店东", "Gaobeidian East", "GDP"),
                Station("保定东", "Baoding East", "BDP"),
                Station("定州东", "Dingzhou East", "DDP"),
                Station("正定机场", "Zhengding Airport", "ZDP"),
                Station("石家庄", "Shijiazhuang", "SJP"),
                Station("高邑西", "Gaoyi West", "GYP"),
                Station("邢台东", "Xingtai East", "XTP"),
                Station("邯郸东", "Handan East", "HDP"),
                Station("安阳东", "Anyang East", "AYP"),
                Station("鹤壁东", "Hebi East", "HBP"),
                Station("新乡东", "Xinxiang East", "XXP"),
                Station("郑州东", "Zhengzhou East", "ZZP"),
                Station("许昌东", "Xuchang East", "XCP"),
                Station("漯河西", "Luohe West", "LHP"),
                Station("驻马店西", "Zhumadian West", "ZNP"),
                Station("明港东", "Minggang East", "MGP"),
                Station("信阳东", "Xinyang East", "XYP"),
                Station("孝感北", "Xiaogan North", "XGP"),
                Station("武汉", "Wuhan", "WHN"),
                Station("咸宁北", "Xianning North", "XNN"),
                Station("赤壁北", "Chibi North", "CBN"),
                Station("岳阳东", "Yueyang East", "YYD"),
                Station("汨罗东", "Miluo East", "MLD"),
                Station("长沙南", "Changsha South", "CSN"),
                Station("株洲西", "Zhuzhou West", "ZZW"),
                Station("衡阳东", "Hengyang East", "HYD"),
                Station("郴州西", "Chenzhou West", "CZW"),
                Station("韶关", "Shaoguan", "SG"),
                Station("清远", "Qingyuan", "QY"),
                Station("广州南", "Guangzhou South", "GZN")
            ),
            segmentPrices = mapOf(
                "北京西-涿州东" to 15.0,
                "涿州东-高碑店东" to 10.0,
                "高碑店东-保定东" to 20.0,
                "保定东-定州东" to 15.0,
                "定州东-正定机场" to 12.0,
                "正定机场-石家庄" to 8.0,
                "石家庄-高邑西" to 15.0,
                "高邑西-邢台东" to 18.0,
                "邢台东-邯郸东" to 12.0,
                "邯郸东-安阳东" to 15.0,
                "安阳东-鹤壁东" to 10.0,
                "鹤壁东-新乡东" to 12.0,
                "新乡东-郑州东" to 15.0,
                "郑州东-许昌东" to 18.0,
                "许昌东-漯河西" to 15.0,
                "漯河西-驻马店西" to 20.0,
                "驻马店西-明港东" to 12.0,
                "明港东-信阳东" to 15.0,
                "信阳东-孝感北" to 18.0,
                "孝感北-武汉" to 20.0,
                "武汉-咸宁北" to 25.0,
                "咸宁北-赤壁北" to 15.0,
                "赤壁北-岳阳东" to 35.0,
                "岳阳东-汨罗东" to 20.0,
                "汨罗东-长沙南" to 25.0,
                "长沙南-株洲西" to 15.0,
                "株洲西-衡阳东" to 45.0,
                "衡阳东-郴州西" to 35.0,
                "郴州西-韶关" to 40.0,
                "韶关-清远" to 30.0,
                "清远-广州南" to 25.0
            ),
            totalPrice = 650.0,
            totalDuration = "8小时15分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(jingguangRoute)
        
        // 沪昆高铁（上海虹桥-昆明南）
        val hukunRoute = RailwayRoute(
            routeId = "HUKUN001",
            routeName = "沪昆高铁",
            stations = listOf(
                Station("上海虹桥", "Shanghai Hongqiao", "AOH"),
                Station("松江南", "Songjiang South", "SJH"),
                Station("金山北", "Jinshan North", "JSH"),
                Station("嘉善南", "Jiashan South", "JSH"),
                Station("嘉兴南", "Jiaxing South", "JXH"),
                Station("桐乡", "Tongxiang", "TXH"),
                Station("海宁西", "Haining West", "HNH"),
                Station("余杭", "Yuhang", "YUH"),
                Station("杭州东", "Hangzhou East", "HGH"),
                Station("诸暨", "Zhuji", "ZJH"),
                Station("义乌", "Yiwu", "YWH"),
                Station("金华", "Jinhua", "JHH"),
                Station("龙游", "Longyou", "LYH"),
                Station("衢州", "Quzhou", "QZH"),
                Station("江山", "Jiangshan", "JSH"),
                Station("玉山南", "Yushan South", "YSH"),
                Station("上饶", "Shangrao", "SRG"),
                Station("弋阳", "Yiyang", "YYG"),
                Station("鹰潭北", "Yingtan North", "YTG"),
                Station("进贤南", "Jinxian South", "JXG"),
                Station("南昌西", "Nanchang West", "NXG"),
                Station("高安", "Gao'an", "GAG"),
                Station("新余北", "Xinyu North", "XBG"),
                Station("宜春", "Yichun", "YCG"),
                Station("萍乡北", "Pingxiang North", "PXG"),
                Station("醴陵东", "Liling East", "LDG"),
                Station("长沙南", "Changsha South", "CSQ"),
                Station("湘潭北", "Xiangtan North", "XTQ"),
                Station("韶山南", "Shaoshan South", "SSQ"),
                Station("娄底南", "Loudi South", "LDQ"),
                Station("邵阳北", "Shaoyang North", "SYQ"),
                Station("新化南", "Xinhua South", "XHQ"),
                Station("溆浦南", "Xupu South", "XPQ"),
                Station("怀化南", "Huaihua South", "HHQ"),
                Station("芷江", "Zhijiang", "ZJQ"),
                Station("新晃西", "Xinhuang West", "XHQ"),
                Station("铜仁南", "Tongren South", "TRQ"),
                Station("三穗", "Sansui", "SSQ"),
                Station("凯里南", "Kaili South", "KLQ"),
                Station("贵定北", "Guiding North", "GDQ"),
                Station("贵阳北", "Guiyang North", "KQW"),
                Station("平坝南", "Pingba South", "PBQ"),
                Station("安顺西", "Anshun West", "ASQ"),
                Station("关岭", "Guanling", "GLQ"),
                Station("普安县", "Pu'an County", "PAQ"),
                Station("盘州", "Panzhou", "PZQ"),
                Station("富源北", "Fuyuan North", "FYQ"),
                Station("曲靖北", "Qujing North", "QJQ"),
                Station("嵩明", "Songming", "SMQ"),
                Station("昆明南", "Kunming South", "KOM")
            ),
            segmentPrices = mapOf(
                "上海虹桥-松江南" to 8.0,
                "松江南-金山北" to 6.0,
                "金山北-嘉善南" to 8.0,
                "嘉善南-嘉兴南" to 6.0,
                "嘉兴南-桐乡" to 8.0,
                "桐乡-海宁西" to 6.0,
                "海宁西-余杭" to 8.0,
                "余杭-杭州东" to 6.0,
                "杭州东-诸暨" to 15.0,
                "诸暨-义乌" to 12.0,
                "义乌-金华" to 15.0,
                "金华-龙游" to 12.0,
                "龙游-衢州" to 8.0,
                "衢州-江山" to 6.0,
                "江山-玉山南" to 8.0,
                "玉山南-上饶" to 10.0,
                "上饶-弋阳" to 8.0,
                "弋阳-鹰潭北" to 12.0,
                "鹰潭北-进贤南" to 15.0,
                "进贤南-南昌西" to 8.0,
                "南昌西-高安" to 12.0,
                "高安-新余北" to 15.0,
                "新余北-宜春" to 12.0,
                "宜春-萍乡北" to 15.0,
                "萍乡北-醴陵东" to 8.0,
                "醴陵东-长沙南" to 12.0,
                "长沙南-湘潭北" to 8.0,
                "湘潭北-韶山南" to 12.0,
                "韶山南-娄底南" to 15.0,
                "娄底南-邵阳北" to 18.0,
                "邵阳北-新化南" to 20.0,
                "新化南-溆浦南" to 15.0,
                "溆浦南-怀化南" to 12.0,
                "怀化南-芷江" to 8.0,
                "芷江-新晃西" to 10.0,
                "新晃西-铜仁南" to 12.0,
                "铜仁南-三穗" to 15.0,
                "三穗-凯里南" to 18.0,
                "凯里南-贵定北" to 12.0,
                "贵定北-贵阳北" to 15.0,
                "贵阳北-平坝南" to 12.0,
                "平坝南-安顺西" to 8.0,
                "安顺西-关岭" to 10.0,
                "关岭-普安县" to 12.0,
                "普安县-盘州" to 15.0,
                "盘州-富源北" to 8.0,
                "富源北-曲靖北" to 12.0,
                "曲靖北-嵩明" to 15.0,
                "嵩明-昆明南" to 8.0
            ),
            totalPrice = 450.0,
            totalDuration = "10小时30分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(hukunRoute)
        
        // 哈大高铁（哈尔滨西-大连北）
        val hadaRoute = RailwayRoute(
            routeId = "HADA001",
            routeName = "哈大高铁",
            stations = listOf(
                Station("哈尔滨西", "Harbin West", "VAB"),
                Station("双城北", "Shuangcheng North", "SCB"),
                Station("扶余北", "Fuyu North", "FYB"),
                Station("德惠西", "Dehui West", "DHB"),
                Station("长春西", "Changchun West", "CRT"),
                Station("公主岭南", "Gongzhuling South", "GZQ"),
                Station("四平东", "Siping East", "SPT"),
                Station("昌图西", "Changtu West", "CTQ"),
                Station("开原西", "Kaiyuan West", "KYT"),
                Station("铁岭西", "Tieling West", "TLT"),
                Station("沈阳北", "Shenyang North", "SBT"),
                Station("辽阳", "Liaoyang", "LYT"),
                Station("鞍山西", "Anshan West", "AST"),
                Station("海城西", "Haicheng West", "HCT"),
                Station("营口东", "Yingkou East", "YKT"),
                Station("盖州西", "Gaizhou West", "GZT"),
                Station("鲅鱼圈", "Bayuquan", "BYT"),
                Station("瓦房店西", "Wafangdian West", "WDT"),
                Station("普湾", "Puwan", "PWT"),
                Station("大连北", "Dalian North", "DFT")
            ),
            segmentPrices = mapOf(
                "哈尔滨西-双城北" to 12.0,
                "双城北-扶余北" to 15.0,
                "扶余北-德惠西" to 18.0,
                "德惠西-长春西" to 20.0,
                "长春西-公主岭南" to 15.0,
                "公主岭南-四平东" to 12.0,
                "四平东-昌图西" to 18.0,
                "昌图西-开原西" to 10.0,
                "开原西-铁岭西" to 12.0,
                "铁岭西-沈阳北" to 15.0,
                "沈阳北-辽阳" to 18.0,
                "辽阳-鞍山西" to 12.0,
                "鞍山西-海城西" to 15.0,
                "海城西-营口东" to 18.0,
                "营口东-盖州西" to 12.0,
                "盖州西-鲅鱼圈" to 10.0,
                "鲅鱼圈-瓦房店西" to 15.0,
                "瓦房店西-普湾" to 8.0,
                "普湾-大连北" to 12.0
            ),
            totalPrice = 280.0,
            totalDuration = "3小时30分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(hadaRoute)
        
        // 西成高铁（西安北-成都东）
        val xichengRoute = RailwayRoute(
            routeId = "XICHENG001",
            routeName = "西成高铁",
            stations = listOf(
                Station("西安北", "Xi'an North", "EAY"),
                Station("阿房宫", "Epang Palace", "EAY"),
                Station("鄠邑", "Huyi", "HYQ"),
                Station("佛坪", "Foping", "FPQ"),
                Station("洋县西", "Yangxian West", "YXQ"),
                Station("城固北", "Chenggu North", "CGQ"),
                Station("汉中", "Hanzhong", "HZQ"),
                Station("新集", "Xinji", "XJQ"),
                Station("宁强南", "Ningqiang South", "NQQ"),
                Station("朝天", "Chaotian", "CTQ"),
                Station("广元", "Guangyuan", "GYW"),
                Station("剑门关", "Jianmenguan", "JMQ"),
                Station("青川", "Qingchuan", "QCQ"),
                Station("江油北", "Jiangyou North", "JYB"),
                Station("江油", "Jiangyou", "JYW"),
                Station("青莲", "Qinglian", "QLQ"),
                Station("绵阳", "Mianyang", "MYW"),
                Station("罗江东", "Luodong East", "LDQ"),
                Station("德阳", "Deyang", "DYW"),
                Station("广汉北", "Guanghan North", "GHQ"),
                Station("青白江东", "Qingbaijiang East", "QBQ"),
                Station("新都东", "Xindu East", "XDQ"),
                Station("成都东", "Chengdu East", "ICW")
            ),
            segmentPrices = mapOf(
                "西安北-阿房宫" to 8.0,
                "阿房宫-鄠邑" to 12.0,
                "鄠邑-佛坪" to 18.0,
                "佛坪-洋县西" to 15.0,
                "洋县西-城固北" to 10.0,
                "城固北-汉中" to 8.0,
                "汉中-新集" to 12.0,
                "新集-宁强南" to 15.0,
                "宁强南-朝天" to 18.0,
                "朝天-广元" to 12.0,
                "广元-剑门关" to 15.0,
                "剑门关-青川" to 18.0,
                "青川-江油北" to 20.0,
                "江油北-江油" to 8.0,
                "江油-青莲" to 6.0,
                "青莲-绵阳" to 12.0,
                "绵阳-罗江东" to 10.0,
                "罗江东-德阳" to 8.0,
                "德阳-广汉北" to 12.0,
                "广汉北-青白江东" to 10.0,
                "青白江东-新都东" to 8.0,
                "新都东-成都东" to 6.0
            ),
            totalPrice = 250.0,
            totalDuration = "4小时15分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(xichengRoute)
        
        // 兰新高铁（兰州西-乌鲁木齐）
        val lanxinRoute = RailwayRoute(
            routeId = "LANXIN001",
            routeName = "兰新高铁",
            stations = listOf(
                Station("兰州西", "Lanzhou West", "LAJ"),
                Station("西固", "Xigu", "XGJ"),
                Station("永登", "Yongdeng", "YDJ"),
                Station("天祝", "Tianzhu", "TZJ"),
                Station("古浪", "Gulang", "GLJ"),
                Station("武威", "Wuwei", "WWJ"),
                Station("金昌", "Jinchang", "JCJ"),
                Station("山丹", "Shandan", "SDJ"),
                Station("张掖西", "Zhangye West", "ZYJ"),
                Station("临泽南", "Linze South", "LZJ"),
                Station("高台南", "Gaotai South", "GTJ"),
                Station("酒泉南", "Jiuquan South", "JQJ"),
                Station("嘉峪关南", "Jiayuguan South", "JYJ"),
                Station("清泉", "Qingquan", "QQJ"),
                Station("玉门", "Yumen", "YMJ"),
                Station("柳园南", "Liuyuan South", "LYJ"),
                Station("哈密", "Hami", "HMJ"),
                Station("吐哈", "Tuha", "THJ"),
                Station("鄯善北", "Shanshan North", "SSJ"),
                Station("吐鲁番北", "Turpan North", "TPJ"),
                Station("乌鲁木齐", "Urumqi", "WAR")
            ),
            segmentPrices = mapOf(
                "兰州西-西固" to 8.0,
                "西固-永登" to 12.0,
                "永登-天祝" to 15.0,
                "天祝-古浪" to 18.0,
                "古浪-武威" to 20.0,
                "武威-金昌" to 15.0,
                "金昌-山丹" to 18.0,
                "山丹-张掖西" to 12.0,
                "张掖西-临泽南" to 8.0,
                "临泽南-高台南" to 10.0,
                "高台南-酒泉南" to 12.0,
                "酒泉南-嘉峪关南" to 8.0,
                "嘉峪关南-清泉" to 15.0,
                "清泉-玉门" to 18.0,
                "玉门-柳园南" to 20.0,
                "柳园南-哈密" to 25.0,
                "哈密-吐哈" to 15.0,
                "吐哈-鄯善北" to 18.0,
                "鄯善北-吐鲁番北" to 20.0,
                "吐鲁番北-乌鲁木齐" to 22.0
            ),
            totalPrice = 320.0,
            totalDuration = "11小时30分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(lanxinRoute)
        
        // 郑西高铁（郑州东-西安北）
        val zhengxiRoute = RailwayRoute(
            routeId = "ZHENGXI001",
            routeName = "郑西高铁",
            stations = listOf(
                Station("郑州东", "Zhengzhou East", "ZAF"),
                Station("郑州西", "Zhengzhou West", "ZAF"),
                Station("巩义南", "Gongyi South", "GYF"),
                Station("洛阳龙门", "Luoyang Longmen", "LYF"),
                Station("渑池南", "Mianchi South", "MCF"),
                Station("三门峡南", "Sanmenxia South", "SMF"),
                Station("灵宝西", "Lingbao West", "LBF"),
                Station("华山北", "Huashan North", "HSF"),
                Station("渭南北", "Weinan North", "WNF"),
                Station("西安北", "Xi'an North", "EAY")
            ),
            segmentPrices = mapOf(
                "郑州东-郑州西" to 8.0,
                "郑州西-巩义南" to 12.0,
                "巩义南-洛阳龙门" to 15.0,
                "洛阳龙门-渑池南" to 18.0,
                "渑池南-三门峡南" to 12.0,
                "三门峡南-灵宝西" to 10.0,
                "灵宝西-华山北" to 15.0,
                "华山北-渭南北" to 12.0,
                "渭南北-西安北" to 8.0
            ),
            totalPrice = 120.0,
            totalDuration = "2小时15分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(zhengxiRoute)
        
        // 合福高铁（合肥南-福州）
        val hefuRoute = RailwayRoute(
            routeId = "HEFU001",
            routeName = "合福高铁",
            stations = listOf(
                Station("合肥南", "Hefei South", "ENH"),
                Station("长临河", "Changlinhe", "CLH"),
                Station("巢湖东", "Chaohu East", "CHH"),
                Station("无为", "Wuwei", "WWH"),
                Station("铜陵北", "Tongling North", "TLH"),
                Station("南陵", "Nanling", "NLH"),
                Station("泾县", "Jingxian", "JXH"),
                Station("旌德", "Jingde", "JDH"),
                Station("绩溪北", "Jixi North", "JXH"),
                Station("歙县北", "Shexian North", "SXH"),
                Station("黄山北", "Huangshan North", "HSH"),
                Station("婺源", "Wuyuan", "WYH"),
                Station("德兴", "Dexing", "DXH"),
                Station("五府山", "Wufushan", "WFH"),
                Station("武夷山北", "Wuyishan North", "WSH"),
                Station("武夷山东", "Wuyishan East", "WSH"),
                Station("建瓯西", "Jian'ou West", "JOH"),
                Station("南平北", "Nanping North", "NBS"),
                Station("古田北", "Gutian North", "GTH"),
                Station("闽清北", "Minqing North", "MQH"),
                Station("福州", "Fuzhou", "FZS")
            ),
            segmentPrices = mapOf(
                "合肥南-长临河" to 8.0,
                "长临河-巢湖东" to 12.0,
                "巢湖东-无为" to 15.0,
                "无为-铜陵北" to 18.0,
                "铜陵北-南陵" to 12.0,
                "南陵-泾县" to 10.0,
                "泾县-旌德" to 8.0,
                "旌德-绩溪北" to 12.0,
                "绩溪北-歙县北" to 8.0,
                "歙县北-黄山北" to 10.0,
                "黄山北-婺源" to 15.0,
                "婺源-德兴" to 12.0,
                "德兴-五府山" to 8.0,
                "五府山-武夷山北" to 15.0,
                "武夷山北-武夷山东" to 10.0,
                "武夷山东-建瓯西" to 12.0,
                "建瓯西-南平北" to 15.0,
                "南平北-古田北" to 18.0,
                "古田北-闽清北" to 12.0,
                "闽清北-福州" to 15.0
            ),
            totalPrice = 280.0,
            totalDuration = "5小时30分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(hefuRoute)
        
        // 贵广高铁（贵阳北-广州南）
        val guigangRoute = RailwayRoute(
            routeId = "GUIGANG001",
            routeName = "贵广高铁",
            stations = listOf(
                Station("贵阳北", "Guiyang North", "KQW"),
                Station("龙洞堡", "Longdongbao", "LDQ"),
                Station("龙里北", "Longli North", "LLQ"),
                Station("贵定县", "Guiding County", "GDQ"),
                Station("都匀东", "Duyun East", "DYQ"),
                Station("三都县", "Sandu County", "SDQ"),
                Station("榕江", "Rongjiang", "RJQ"),
                Station("从江", "Congjiang", "CJQ"),
                Station("三江南", "Sanjiang South", "SJQ"),
                Station("五通", "Wutong", "WTQ"),
                Station("桂林西", "Guilin West", "GLQ"),
                Station("桂林北", "Guilin North", "GBZ"),
                Station("永福南", "Yongfu South", "YFQ"),
                Station("鹿寨北", "Luzhai North", "LZQ"),
                Station("柳州", "Liuzhou", "LZZ"),
                Station("进德", "Jinde", "JDQ"),
                Station("来宾北", "Laibin North", "LBQ"),
                Station("宾阳", "Binyang", "BYQ"),
                Station("五塘", "Wutang", "WTQ"),
                Station("南宁东", "Nanning East", "NFZ"),
                Station("南宁", "Nanning", "NNZ"),
                Station("南宁西", "Nanning West", "NNZ"),
                Station("隆安东", "Long'an East", "LAQ"),
                Station("平果", "Pingguo", "PGQ"),
                Station("田东北", "Tiandong North", "TDQ"),
                Station("田阳", "Tianyang", "TYQ"),
                Station("百色", "Baise", "BIZ"),
                Station("田林", "Tianlin", "TLQ"),
                Station("隆林", "Longlin", "LLQ"),
                Station("册亨", "Ceheng", "CHQ"),
                Station("安龙", "Anlong", "ALQ"),
                Station("兴义", "Xingyi", "XYQ"),
                Station("广州南", "Guangzhou South", "IZQ")
            ),
            segmentPrices = mapOf(
                "贵阳北-龙洞堡" to 8.0,
                "龙洞堡-龙里北" to 10.0,
                "龙里北-贵定县" to 12.0,
                "贵定县-都匀东" to 15.0,
                "都匀东-三都县" to 18.0,
                "三都县-榕江" to 20.0,
                "榕江-从江" to 15.0,
                "从江-三江南" to 18.0,
                "三江南-五通" to 12.0,
                "五通-桂林西" to 15.0,
                "桂林西-桂林北" to 8.0,
                "桂林北-永福南" to 12.0,
                "永福南-鹿寨北" to 15.0,
                "鹿寨北-柳州" to 18.0,
                "柳州-进德" to 10.0,
                "进德-来宾北" to 12.0,
                "来宾北-宾阳" to 15.0,
                "宾阳-五塘" to 8.0,
                "五塘-南宁东" to 10.0,
                "南宁东-南宁" to 6.0,
                "南宁-南宁西" to 8.0,
                "南宁西-隆安东" to 12.0,
                "隆安东-平果" to 15.0,
                "平果-田东北" to 18.0,
                "田东北-田阳" to 12.0,
                "田阳-百色" to 15.0,
                "百色-田林" to 18.0,
                "田林-隆林" to 20.0,
                "隆林-册亨" to 15.0,
                "册亨-安龙" to 12.0,
                "安龙-兴义" to 10.0,
                "兴义-广州南" to 25.0
            ),
            totalPrice = 420.0,
            totalDuration = "6小时30分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(guigangRoute)
        
        // 武宜高铁（汉口-宜昌东）
        val wuyiRoute = RailwayRoute(
            routeId = "WUYI001",
            routeName = "武宜高铁",
            stations = listOf(
                Station("汉口", "Hankou", "HKN"),
                Station("汉川北", "Hanchuan North", "HBN"),
                Station("天门", "Tianmen", "TMN"),
                Station("京山南", "Jingshan South", "JSN"),
                Station("钟祥南", "Zhongxiang South", "ZSN"),
                Station("荆门西", "Jingmen West", "JMN"),
                Station("当阳西", "Dangyang West", "DYN"),
                Station("宜昌北", "Yichang North", "YBN"),
                Station("宜昌东", "Yichang East", "YCD")
            ),
            segmentPrices = mapOf(
                "汉口-汉川北" to 15.0,
                "汉川北-天门" to 18.0,
                "天门-京山南" to 20.0,
                "京山南-钟祥南" to 22.0,
                "钟祥南-荆门西" to 25.0,
                "荆门西-当阳西" to 18.0,
                "当阳西-宜昌北" to 15.0,
                "宜昌北-宜昌东" to 12.0
            ),
            totalPrice = 145.0,
            totalDuration = "2小时45分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(wuyiRoute)
        
        // 武九客专（武汉-九江）
        val wujiuRoute = RailwayRoute(
            routeId = "WUJIU001",
            routeName = "武九客专",
            stations = listOf(
                Station("武汉", "Wuhan", "WHN"),
                Station("葛店南", "Gedian South", "GDN"),
                Station("华容南", "Huarong South", "HRN"),
                Station("鄂州", "Ezhou", "EZ"),
                Station("鄂州东", "Ezhou East", "EZD"),
                Station("花湖", "Huahu", "HH"),
                Station("黄石北", "Huangshi North", "HSN"),
                Station("大冶北", "Daye North", "DYN"),
                Station("白沙铺", "Baishapu", "BSP"),
                Station("阳新", "Yangxin", "YX"),
                Station("枫林", "Fenglin", "FL"),
                Station("瑞昌西", "Ruichang West", "RCX"),
                Station("柴桑", "Chaisang", "CS"),
                Station("庐山", "Lushan", "LS"),
                Station("九江", "Jiujiang", "JJ")
            ),
            segmentPrices = mapOf(
                "武汉-葛店南" to 22.0,
                "葛店南-华容南" to 10.0,
                "华容南-鄂州" to 12.0,
                "鄂州-鄂州东" to 6.0,
                "鄂州东-花湖" to 8.0,
                "花湖-黄石北" to 10.0,
                "黄石北-大冶北" to 8.0,
                "大冶北-白沙铺" to 12.0,
                "白沙铺-阳新" to 10.0,
                "阳新-枫林" to 8.0,
                "枫林-瑞昌西" to 15.0,
                "瑞昌西-柴桑" to 8.0,
                "柴桑-庐山" to 10.0,
                "庐山-九江" to 8.0
            ),
            totalPrice = 155.0,
            totalDuration = "1小时30分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(wujiuRoute)
        
        // 昌九城际（南昌-九江）
        val changjiuRoute = RailwayRoute(
            routeId = "CHANGJIU001",
            routeName = "昌九城际",
            stations = listOf(
                Station("南昌", "Nanchang", "NC"),
                Station("南昌西", "Nanchang West", "NCX"),
                Station("共青城", "Gongqingcheng", "GQC"),
                Station("德安", "De'an", "DA"),
                Station("永修", "Yongxiu", "YX"),
                Station("庐山", "Lushan", "LS"),
                Station("九江", "Jiujiang", "JJ")
            ),
            segmentPrices = mapOf(
                "南昌-南昌西" to 8.0,
                "南昌西-共青城" to 25.0,
                "共青城-德安" to 15.0,
                "德安-永修" to 12.0,
                "永修-庐山" to 18.0,
                "庐山-九江" to 10.0
            ),
            totalPrice = 88.0,
            totalDuration = "1小时15分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(changjiuRoute)
        
        // 汉十高铁（汉口-十堰东）
        val hanshiRoute = RailwayRoute(
            routeId = "HANSHI001",
            routeName = "汉十高铁",
            stations = listOf(
                Station("汉口", "Hankou", "HKN"),
                Station("孝感东", "Xiaogan East", "XGD"),
                Station("云梦东", "Yunmeng East", "YMD"),
                Station("安陆西", "Anlu West", "ALX"),
                Station("随州南", "Suizhou South", "SZN"),
                Station("随县", "Suixian", "SX"),
                Station("枣阳", "Zaoyang", "ZY"),
                Station("襄阳东", "Xiangyang East", "XYD"),
                Station("隆中", "Longzhong", "LZ"),
                Station("谷城北", "Gucheng North", "GCB"),
                Station("丹江口南", "Danjiangkou South", "DJN"),
                Station("武当山西", "Wudangshan West", "WDS"),
                Station("十堰东", "Shiyan East", "SYD")
            ),
            segmentPrices = mapOf(
                "汉口-孝感东" to 15.0,
                "孝感东-云梦东" to 12.0,
                "云梦东-安陆西" to 18.0,
                "安陆西-随州南" to 25.0,
                "随州南-随县" to 8.0,
                "随县-枣阳" to 20.0,
                "枣阳-襄阳东" to 15.0,
                "襄阳东-隆中" to 8.0,
                "隆中-谷城北" to 18.0,
                "谷城北-丹江口南" to 22.0,
                "丹江口南-武当山西" to 15.0,
                "武当山西-十堰东" to 12.0
            ),
            totalPrice = 188.0,
            totalDuration = "2小时15分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(hanshiRoute)
        
        // 徐兰高铁（徐州东-兰州西）
        val xulanRoute = RailwayRoute(
            routeId = "XULAN001",
            routeName = "徐兰高铁",
            stations = listOf(
                Station("徐州东", "Xuzhou East", "UUH"),
                Station("徐州", "Xuzhou", "XCH"),
                Station("萧县北", "Xiaoxian North", "XBN"),
                Station("永城北", "Yongcheng North", "YCN"),
                Station("砀山南", "Dangshan South", "DSN"),
                Station("商丘", "Shangqiu", "SQF"),
                Station("民权北", "Minquan North", "MQN"),
                Station("兰考南", "Lankao South", "LKN"),
                Station("开封北", "Kaifeng North", "KFN"),
                Station("郑州东", "Zhengzhou East", "ZAF"),
                Station("郑州西", "Zhengzhou West", "ZAF"),
                Station("巩义南", "Gongyi South", "GYF"),
                Station("洛阳龙门", "Luoyang Longmen", "LYF"),
                Station("渑池南", "Mianchi South", "MCF"),
                Station("三门峡南", "Sanmenxia South", "SMF"),
                Station("灵宝西", "Lingbao West", "LBF"),
                Station("华山北", "Huashan North", "HSF"),
                Station("渭南北", "Weinan North", "WNF"),
                Station("西安北", "Xi'an North", "EAY"),
                Station("咸阳秦都", "Xianyang Qindu", "XAY"),
                Station("杨陵南", "Yangling South", "YLY"),
                Station("岐山", "Qishan", "QSH"),
                Station("宝鸡南", "Baoji South", "BJY"),
                Station("东岔", "Dongcha", "DCY"),
                Station("天水南", "Tianshui South", "TSY"),
                Station("秦安", "Qin'an", "QAY"),
                Station("通渭", "Tongwei", "TWY"),
                Station("定西北", "Dingxi North", "DXY"),
                Station("榆中", "Yuzhong", "YZY"),
                Station("兰州西", "Lanzhou West", "LAJ")
            ),
            segmentPrices = mapOf(
                "徐州东-徐州" to 8.0,
                "徐州-萧县北" to 12.0,
                "萧县北-永城北" to 15.0,
                "永城北-砀山南" to 18.0,
                "砀山南-商丘" to 20.0,
                "商丘-民权北" to 15.0,
                "民权北-兰考南" to 12.0,
                "兰考南-开封北" to 10.0,
                "开封北-郑州东" to 18.0,
                "郑州东-郑州西" to 8.0,
                "郑州西-巩义南" to 12.0,
                "巩义南-洛阳龙门" to 15.0,
                "洛阳龙门-渑池南" to 18.0,
                "渑池南-三门峡南" to 12.0,
                "三门峡南-灵宝西" to 10.0,
                "灵宝西-华山北" to 15.0,
                "华山北-渭南北" to 12.0,
                "渭南北-西安北" to 8.0,
                "西安北-咸阳秦都" to 8.0,
                "咸阳秦都-杨陵南" to 12.0,
                "杨陵南-岐山" to 15.0,
                "岐山-宝鸡南" to 18.0,
                "宝鸡南-东岔" to 20.0,
                "东岔-天水南" to 25.0,
                "天水南-秦安" to 15.0,
                "秦安-通渭" to 18.0,
                "通渭-定西北" to 20.0,
                "定西北-榆中" to 15.0,
                "榆中-兰州西" to 12.0
            ),
            totalPrice = 485.0,
            totalDuration = "6小时45分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(xulanRoute)
        
        // 襄荆高铁（襄阳东-荆州）
        val xiangjingRoute = RailwayRoute(
            routeId = "XIANGJING001",
            routeName = "襄荆高铁",
            stations = listOf(
                Station("襄阳东", "Xiangyang East", "XYE"),
                Station("宜城", "Yicheng", "YC"),
                Station("荆门西", "Jingmen West", "JMW"),
                Station("沙洋西", "Shayang West", "SYW"),
                Station("荆州", "Jingzhou", "JZ")
            ),
            segmentPrices = mapOf(
                "襄阳东-宜城" to 25.0,
                "宜城-荆门西" to 30.0,
                "荆门西-沙洋西" to 20.0,
                "沙洋西-荆州" to 25.0
            ),
            totalPrice = 100.0,
            totalDuration = "1小时20分",
            routeType = RouteType.HIGH_SPEED
        )
        RailwayRouteManager.addRoute(xiangjingRoute)
        
        // 武汉枢纽连接线（武汉-汉口-武昌-武汉东）
        val wuhanHubRoute = RailwayRoute(
            routeId = "WUHANHUB001",
            routeName = "武汉枢纽连接线",
            stations = listOf(
                Station("武汉", "Wuhan", "WH"),
                Station("汉口", "Hankou", "HK"),
                Station("武昌", "Wuchang", "WC"),
                Station("武汉东", "Wuhan East", "WHD")
            ),
            segmentPrices = mapOf(
                "武汉-汉口" to 5.0,
                "汉口-武昌" to 5.0,
                "武昌-武汉东" to 5.0
            ),
            totalPrice = 15.0,
            totalDuration = "30分",
            routeType = RouteType.INTERCITY
        )
        RailwayRouteManager.addRoute(wuhanHubRoute)
        
        // 南昌枢纽联络线（南昌西-南昌东-南昌南）
        val nanchangHubRoute = RailwayRoute(
            routeId = "NANCHANGHUB001",
            routeName = "南昌枢纽联络线",
            stations = listOf(
                Station("南昌西", "Nanchang West", "NCW"),
                Station("南昌东", "Nanchang East", "NCE"),
                Station("南昌南", "Nanchang South", "NCS")
            ),
            segmentPrices = mapOf(
                "南昌西-南昌东" to 5.0,
                "南昌东-南昌南" to 5.0
            ),
            totalPrice = 10.0,
            totalDuration = "20分",
            routeType = RouteType.INTERCITY
        )
        RailwayRouteManager.addRoute(nanchangHubRoute)

        // Keep post-2024 commissioned passenger corridors in the same route graph.
        LatestRailwayNetwork.install()
        installConventionalTrunkRoutes()
    }

    /**
     * Conventional corridors use their own physical station catalog.  Names such as
     * 北京/上海 are intentionally not substituted with 北京南/上海虹桥, so a K/T/Z
     * service cannot be routed over a high-speed stop by name.
     */
    private fun installConventionalTrunkRoutes() {
        listOf(
            conventionalRoute(
                "CONV_JINGHU", "京沪铁路", 220.0, "14小时30分",
                listOf("北京", "廊坊北", "天津", "静海", "沧州", "德州", "济南", "泰山", "兖州", "枣庄西", "徐州", "宿州", "蚌埠", "滁州北", "南京", "镇江", "常州", "无锡", "苏州", "昆山", "上海")
            ),
            conventionalRoute(
                "CONV_JINGGUANG", "京广铁路", 260.0, "21小时10分",
                listOf("北京", "保定", "石家庄", "邯郸", "安阳", "新乡", "郑州", "漯河", "驻马店", "信阳", "孝感", "武昌", "岳阳", "长沙", "株洲", "衡阳", "郴州", "韶关东", "广州")
            ),
            conventionalRoute(
                "CONV_LONGHAI", "陇海铁路", 245.0, "18小时20分",
                listOf("连云港东", "徐州", "商丘", "开封", "郑州", "洛阳", "三门峡", "灵宝", "华山", "渭南", "西安", "宝鸡", "天水", "甘谷", "陇西", "定西", "兰州")
            ),
            conventionalRoute(
                "CONV_HUKUN", "沪昆铁路", 340.0, "28小时40分",
                listOf("上海", "嘉兴", "杭州", "金华", "上饶", "鹰潭", "南昌", "萍乡", "株洲", "湘潭", "娄底", "怀化", "玉屏", "凯里", "贵阳", "安顺", "六盘水", "宣威", "曲靖", "昆明")
            ),
            conventionalRoute(
                "CONV_JINGJIU", "京九铁路", 285.0, "23小时30分",
                listOf("北京西", "霸州", "衡水", "聊城", "菏泽", "商丘南", "阜阳", "九江", "南昌", "吉安", "赣州", "龙川", "惠州", "深圳")
            ),
            conventionalRoute(
                "CONV_JIAOLIU", "焦柳铁路", 300.0, "25小时10分",
                listOf("焦作", "济源", "洛阳", "汝州", "平顶山西", "南阳", "襄阳", "荆门", "石门县北", "张家界", "吉首", "怀化", "靖州", "融安", "柳州")
            ),
            conventionalRoute(
                "CONV_LANXIN", "兰新铁路", 315.0, "30小时20分",
                listOf("兰州", "武威", "金昌", "张掖", "嘉峪关", "玉门", "柳园", "哈密", "吐鲁番", "乌鲁木齐")
            ),
            conventionalRoute(
                "CONV_QINGZANG", "青藏铁路", 235.0, "20小时45分",
                listOf("西宁", "格尔木", "五道梁", "安多", "那曲", "拉萨")
            ),
            conventionalRoute(
                "CONV_HADA", "哈大铁路", 205.0, "14小时10分",
                listOf("哈尔滨", "长春", "四平", "铁岭", "沈阳", "辽阳", "鞍山", "营口", "大连")
            ),
            conventionalRoute(
                "CONV_CHENGYU", "成渝铁路", 130.0, "10小时20分",
                listOf("成都", "简阳", "资阳", "内江", "隆昌", "荣昌", "永川", "重庆")
            ),
            conventionalRoute(
                "CONV_XIANGYU", "襄渝铁路", 190.0, "16小时40分",
                listOf("襄阳", "十堰", "安康", "万源", "达州", "渠县", "广安", "重庆")
            )
        ).forEach(RailwayRouteManager::addRoute)
    }

    private fun conventionalRoute(
        id: String,
        name: String,
        totalPrice: Double,
        duration: String,
        stationNames: List<String>
    ): RailwayRoute {
        val stations = stationNames.mapIndexed { index, stationName ->
            Station(
                name = stationName,
                code = "CV_${id.takeLast(4)}_${index + 1}",
                network = StationNetwork.CONVENTIONAL
            )
        }
        val baseSegmentPrice = totalPrice / (stations.size - 1).coerceAtLeast(1)
        val prices = stations.zipWithNext().associate { (from, to) ->
            "${from.name}-${to.name}" to baseSegmentPrice
        }
        return RailwayRoute(
            routeId = id,
            routeName = name,
            stations = stations,
            segmentPrices = prices,
            totalPrice = totalPrice,
            totalDuration = duration,
            routeType = RouteType.CONVENTIONAL
        )
    }
    
    /**
     * 获取所有线路
     */
    fun getAllRoutes(): List<RailwayRoute> {
        return RailwayRouteManager.getAllRoutes()
    }
    
    /**
     * 根据起点和终点查找线路
     */
    fun findRoute(fromStation: String, toStation: String): RailwayRoute? {
        return RailwayRouteManager.findRoute(fromStation, toStation)
    }
    
    /**
     * 获取两个车站之间的价格
     */
    fun getPriceBetweenStations(fromStation: String, toStation: String): Double {
        return RailwayRouteManager.getPriceBetweenStations(fromStation, toStation)
    }
    
    /**
     * 获取两个车站之间的耗时
     */
    fun getDurationBetweenStations(fromStation: String, toStation: String): String {
        return RailwayRouteManager.getDurationBetweenStations(fromStation, toStation)
    }
    
    /**
     * 获取线路的途径车站
     */
    fun getRouteStations(fromStation: String, toStation: String): List<String> {
        return RailwayRouteManager.getRouteStations(fromStation, toStation)
    }
}
