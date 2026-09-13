package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.RailwayRoute
import com.railway.ticketsystem.model.RailwayRouteManager
import com.railway.ticketsystem.model.RouteType
import com.railway.ticketsystem.model.Station
import com.railway.ticketsystem.model.StationNetwork

/**
 * Offline catalogue of the remaining national passenger corridors.
 *
 * It deliberately contains only lines that have opened passenger service.  Lines
 * still under construction or merely planned are kept out of the search graph so
 * they cannot generate an impossible train.  High-speed and intercity entries
 * share the high-speed physical graph; conventional entries are built with their
 * own StationNetwork and never create an edge in that graph.
 */
object NationalPassengerRailCatalog {
    private data class Entry(
        val id: String,
        val name: String,
        val type: RouteType,
        val totalPrice: Double,
        val duration: String,
        val stationNames: List<String>
    )

    @Volatile
    private var installed = false

    private val entries: List<Entry> by lazy { highSpeedEntries() + conventionalEntries() }

    /** Exposed for station search/import after routes are installed. */
    val stationDirectory: List<Station> by lazy {
        entries.asSequence()
            .flatMap { entry -> entry.stationNames.asSequence().map { name -> station(name, entry) } }
            .distinctBy { it.name to it.network }
            .toList()
    }

    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            entries.map(::toRoute).forEach(RailwayRouteManager::addRoute)
            installed = true
        }
    }

    private fun highSpeedEntries(): List<Entry> = listOf(
        h("NPR_JINGJIN", "京津城际铁路", 55.0, "33分钟", "北京南", "武清", "天津"),
        h("NPR_JINGZHANG", "京张高铁", 115.0, "1小时10分钟", "北京北", "清河", "昌平北", "八达岭长城", "怀来", "张家口"),
        h("NPR_JINGXIONG", "京雄城际铁路", 72.0, "50分钟", "北京大兴", "大兴机场", "雄安"),
        i("NPR_JINGTANG", "京唐城际铁路", 84.0, "1小时05分钟", "北京城市副中心", "香河", "宝坻", "唐山"),
        i("NPR_JINXING", "津兴城际铁路", 62.0, "45分钟", "天津西", "胜芳", "固安东", "大兴机场"),
        h("NPR_DAZHANG", "大张高铁", 86.0, "1小时15分钟", "大同南", "天镇", "阳高南", "张家口"),
        h("NPR_SHITAI", "石太客专", 93.0, "1小时08分钟", "石家庄", "阳泉北", "太原南"),
        h("NPR_DAXI", "大西高铁", 185.0, "2小时30分钟", "太原南", "临汾西", "侯马西", "运城北", "大荔", "渭南北", "西安北"),
        h("NPR_ZHENGTAI", "郑太高铁", 145.0, "2小时15分钟", "郑州", "焦作", "晋城东", "长治东", "襄垣东", "太谷东", "太原南"),
        h("NPR_ZHENGJI", "郑济高铁", 135.0, "1小时45分钟", "郑州东", "新乡东", "卫辉南", "滑浚", "内黄", "濮阳东", "莘县", "聊城西", "茌平南", "长清", "济南西"),
        h("NPR_ZHENGFU", "郑阜高铁", 112.0, "1小时20分钟", "郑州东", "郑州航空港", "周口东", "沈丘北", "界首南", "临泉", "阜阳西"),
        h("NPR_SHANGHANG", "商杭高铁", 245.0, "3小时10分钟", "商丘", "亳州南", "阜阳西", "淮南南", "合肥南", "巢湖东", "芜湖", "宣城", "湖州", "德清", "杭州东"),
        h("NPR_HEBENG", "合蚌高铁", 78.0, "38分钟", "合肥南", "水家湖", "淮南东", "蚌埠南"),
        h("NPR_HEAN", "合安高铁", 96.0, "1小时05分钟", "合肥南", "舒城东", "庐江西", "桐城东", "安庆"),
        h("NPR_NINGHANG", "宁杭高铁", 137.0, "1小时25分钟", "南京南", "江宁", "溧阳", "宜兴", "湖州", "德清", "杭州东"),
        h("NPR_HANGYONG", "杭甬高铁", 72.0, "45分钟", "杭州东", "绍兴北", "余姚北", "宁波"),
        h("NPR_YONGTAIWEN", "甬台温铁路", 118.0, "1小时25分钟", "宁波", "宁海", "三门县", "台州", "温岭", "温州南"),
        h("NPR_WENFU", "温福铁路", 142.0, "1小时45分钟", "温州南", "瑞安", "平阳", "苍南", "福鼎", "太姥山", "霞浦", "福安", "宁德", "连江", "福州南"),
        h("NPR_FUXIA", "福厦高铁", 103.0, "55分钟", "福州南", "福清西", "莆田", "泉州东", "泉州南", "厦门北"),
        h("NPR_JINWEN", "金温铁路", 126.0, "1小时35分钟", "金华", "武义北", "永康南", "缙云西", "丽水", "青田", "温州南"),
        h("NPR_JINTAI", "金台铁路", 105.0, "1小时30分钟", "金华", "横店", "磐安", "仙居", "临海", "台州"),
        h("NPR_QUNING", "衢宁铁路", 138.0, "2小时15分钟", "衢州", "龙游", "遂昌", "松阳", "庆元", "屏南", "周宁", "宁德"),
        h("NPR_JICHANGHUANG", "昌景黄高铁", 164.0, "1小时50分钟", "南昌东", "进贤北", "余干", "鄱阳", "乐平北", "景德镇北", "浮梁东", "祁门南", "黟县东", "黄山北"),
        h("NPR_GANRUILONG", "赣瑞龙铁路", 118.0, "1小时45分钟", "赣州", "于都", "会昌北", "瑞金", "长汀南", "冠豸山", "古田会址", "龙岩"),
        h("NPR_NANLONG", "南龙铁路", 128.0, "1小时50分钟", "南平市", "延平", "三明北", "将乐", "泰宁", "建宁县北", "冠豸山", "龙岩"),
        h("NPR_JIQING", "济青高铁", 98.0, "1小时05分钟", "济南东", "邹平", "淄博北", "临淄北", "潍坊北", "高密北", "青岛北"),
        h("NPR_QINGRONG", "青荣城际铁路", 115.0, "1小时45分钟", "青岛北", "即墨北", "莱阳", "海阳北", "烟台南", "牟平", "威海", "荣成"),
        h("NPR_WEILAI", "潍莱高铁", 62.0, "36分钟", "潍坊北", "昌邑", "平度", "莱西"),
        h("NPR_RILAN", "日兰高铁", 218.0, "2小时48分钟", "日照西", "莒南北", "临沂北", "费县北", "蒙山", "曲阜东", "泗水南", "济宁东", "嘉祥北", "菏泽东", "庄寨", "兰考南"),
        i("NPR_QINGYAN", "青盐铁路", 135.0, "1小时55分钟", "青岛北", "红岛", "董家口", "日照西", "岚山西", "赣榆", "连云港"),
        h("NPR_LIANZHEN", "连镇高铁", 156.0, "2小时10分钟", "连云港", "灌南", "淮安东", "宝应", "高邮", "扬州东", "大港南", "丹徒", "镇江"),
        h("NPR_XUYAN", "徐盐高铁", 148.0, "1小时55分钟", "徐州东", "观音机场", "睢宁", "宿迁", "泗阳", "淮安东", "阜宁南", "建湖", "盐城"),
        h("NPR_YANTONG", "盐通高铁", 92.0, "1小时02分钟", "盐城", "东台", "海安", "如皋南", "张家港"),
        i("NPR_HUSUTONG", "沪苏通铁路", 98.0, "1小时20分钟", "上海虹桥", "太仓", "常熟", "张家港", "南通西", "如皋南", "海安"),
        h("NPR_HUSUHU", "沪苏湖高铁", 76.0, "45分钟", "上海虹桥", "松江南", "盛泽", "南浔", "湖州"),
        h("NPR_NANYANJIANG", "南沿江城际铁路", 103.0, "1小时10分钟", "南京南", "句容", "金坛", "武进", "江阴", "张家港"),
        i("NPR_NINGQI", "宁启铁路", 88.0, "1小时20分钟", "南京", "扬州", "泰州", "姜堰", "海安", "如皋", "南通"),
        h("NPR_ZHANGJIHUAI", "张吉怀高铁", 176.0, "2小时30分钟", "张家界西", "芙蓉镇", "古丈西", "吉首东", "凤凰古城", "麻阳西", "怀化南"),
        h("NPR_QIANZHANGCHANG", "黔张常铁路", 205.0, "3小时10分钟", "黔江", "咸丰", "来凤", "龙山北", "桑植", "张家界西", "桃源", "常德"),
        h("NPR_HUAISHAOHENG", "怀邵衡铁路", 154.0, "2小时20分钟", "怀化南", "隆回", "邵阳", "邵东", "衡阳东"),
        i("NPR_CHANGZHUTAN", "长株潭城际铁路", 38.0, "35分钟", "长沙", "株洲", "湘潭"),
        h("NPR_JINGJING", "荆荆高铁", 62.0, "35分钟", "荆门西", "沙洋西", "荆州"),
        h("NPR_HUANGHUANG", "黄黄高铁", 76.0, "46分钟", "黄冈东", "浠水南", "蕲春南", "武穴北", "黄梅东"),
        i("NPR_ZHENGKAI", "郑开城际铁路", 35.0, "25分钟", "郑州东", "贾鲁河", "绿博园", "宋城路", "开封"),
        i("NPR_WUXIAO", "武孝城际铁路", 42.0, "30分钟", "汉口", "后湖", "天河机场", "孝感东"),
        i("NPR_WUXIAN", "武咸城际铁路", 45.0, "32分钟", "武昌", "汤逊湖", "纸坊东", "山坡东", "咸宁南"),
        h("NPR_GUANGSHAN", "广汕高铁", 98.0, "1小时05分钟", "广州新塘", "增城", "罗浮山", "惠州南", "汕尾"),
        h("NPR_SHANSHAN", "汕汕高铁", 82.0, "45分钟", "汕尾", "陆丰东", "潮南", "汕头南", "汕头"),
        h("NPR_MEISHAN", "梅汕铁路", 75.0, "50分钟", "梅州西", "畲江北", "建桥", "丰顺东", "揭阳", "潮汕"),
        i("NPR_SHENZHAN", "深湛铁路江茂段", 168.0, "2小时15分钟", "广州南", "佛山西", "江门", "双水镇", "开平南", "阳江", "茂名", "湛江西"),
        h("NPR_NANGUANG", "南广铁路", 168.0, "2小时30分钟", "广州南", "三水南", "肇庆东", "云浮东", "郁南", "梧州南", "藤县", "平南南", "桂平", "贵港", "南宁东"),
        h("NPR_GUINAN", "贵南高铁", 205.0, "2小时50分钟", "贵阳北", "龙洞堡", "都匀东", "独山东", "荔波", "环江", "河池西", "都安", "马山", "南宁东"),
        h("NPR_NANPING", "南凭高铁", 95.0, "1小时15分钟", "南宁", "吴圩机场", "崇左南", "宁明东", "凭祥东"),
        h("NPR_NANYU", "南玉高铁", 86.0, "1小时05分钟", "南宁东", "横州", "兴业南", "玉林北"),
        h("NPR_YUGUI", "渝贵铁路", 156.0, "2小时20分钟", "重庆西", "綦江东", "桐梓东", "遵义", "息烽", "贵阳北"),
        h("NPR_CHENGGUI", "成贵高铁", 235.0, "3小时30分钟", "成都东", "眉山东", "乐山", "宜宾西", "兴文", "毕节", "大方", "黔西", "贵阳东"),
        h("NPR_CHENGMIANLE", "成绵乐客专", 122.0, "1小时25分钟", "成都东", "广汉北", "德阳", "绵阳", "江油", "青白江东", "眉山东", "乐山"),
        h("NPR_CHENGZIYI", "成自宜高铁", 128.0, "1小时30分钟", "成都东", "天府机场", "资阳北", "自贡", "宜宾"),
        i("NPR_CHUANNAN", "川南城际铁路", 78.0, "55分钟", "内江北", "自贡", "富顺", "泸州"),
        i("NPR_CHENGGUAN", "成灌铁路", 32.0, "32分钟", "成都", "郫县西", "都江堰", "青城山"),
        h("NPR_XIYIN", "银西高铁", 175.0, "2小时40分钟", "西安北", "礼泉南", "永寿西", "彬州东", "庆阳", "环县", "吴忠", "银川"),
        h("NPR_LANZHANG", "兰张高铁", 168.0, "2小时15分钟", "兰州西", "兰州新区", "永登北", "天祝西", "武威东", "金昌", "山丹马场", "张掖西"),
        i("NPR_LANZHONG", "兰中城际铁路", 42.0, "32分钟", "兰州西", "兰州新区", "中川机场"),
        h("NPR_XININGZHANGYE", "兰新高铁西宁—张掖段", 145.0, "2小时10分钟", "西宁", "大通西", "门源", "民乐", "张掖西"),
        h("NPR_CHUANQING", "川青铁路", 185.0, "2小时10分钟", "成都东", "广汉北", "什邡西", "绵竹南", "安州", "高川", "镇江关", "松潘", "黄龙九寨"),
        h("NPR_LIXIANG", "丽香铁路", 88.0, "1小时20分钟", "丽江", "拉市海", "小中甸", "香格里拉"),
        h("NPR_CHUDA", "楚大铁路", 92.0, "1小时25分钟", "昆明", "广通北", "楚雄", "祥云", "大理"),
        h("NPR_MIMENG", "弥蒙高铁", 56.0, "35分钟", "弥勒", "竹园", "蒙自北"),
        h("NPR_HAQI", "哈齐高铁", 125.0, "1小时30分钟", "哈尔滨", "肇东", "安达", "大庆东", "泰康", "齐齐哈尔南"),
        h("NPR_HAMU", "哈牡高铁", 138.0, "1小时35分钟", "哈尔滨", "阿城北", "尚志南", "亚布力西", "横道河子东", "牡丹江"),
        h("NPR_MUJIA", "牡佳客专", 156.0, "2小时15分钟", "牡丹江", "林口南", "鸡西西", "七台河西", "桦南东", "佳木斯"),
        i("NPR_CHANGHUN", "长珲城际铁路", 148.0, "2小时05分钟", "长春", "吉林", "蛟河西", "敦化", "安图西", "延吉西", "珲春"),
        h("NPR_SHENDAN", "沈丹客专", 82.0, "1小时10分钟", "沈阳南", "本溪", "南芬北", "凤城东", "丹东"),
        h("NPR_PANYING", "盘营高铁", 38.0, "27分钟", "盘锦", "营口东", "鲅鱼圈"),
        i("NPR_DANDALIAN", "丹大快速铁路", 92.0, "1小时30分钟", "丹东", "东港北", "庄河北", "皮口", "大连北")
    )

    private fun conventionalEntries(): List<Entry> = listOf(
        c("NPR_CONV_JINGBAO", "京包铁路", 148.0, "12小时10分钟", "北京", "沙城", "张家口", "集宁南", "呼和浩特", "包头"),
        c("NPR_CONV_BAOLAN", "包兰铁路", 176.0, "18小时40分钟", "包头", "乌海", "惠农", "银川", "中卫", "白银西", "兰州"),
        c("NPR_CONV_SHENSHAN", "沈山铁路", 145.0, "12小时30分钟", "沈阳", "新民", "黑山", "沟帮子", "锦州", "葫芦岛", "山海关", "秦皇岛"),
        c("NPR_CONV_JINGTONG", "京通铁路", 158.0, "15小时20分钟", "北京", "怀柔", "密云", "隆化", "赤峰", "通辽"),
        c("NPR_CONV_BINZHOU", "滨洲铁路", 126.0, "13小时10分钟", "哈尔滨", "安达", "大庆", "齐齐哈尔", "扎兰屯", "海拉尔", "满洲里"),
        c("NPR_CONV_BINSUI", "滨绥铁路", 132.0, "10小时30分钟", "哈尔滨", "尚志", "牡丹江", "绥芬河"),
        c("NPR_CONV_TUJIA", "图佳铁路", 115.0, "11小时30分钟", "图们", "延吉", "敦化", "牡丹江", "林口", "佳木斯"),
        c("NPR_CONV_CHANGTU", "长图铁路", 98.0, "10小时10分钟", "长春", "吉林", "蛟河", "敦化", "延吉", "图们"),
        c("NPR_CONV_MEIJI", "梅集铁路", 118.0, "12小时15分钟", "沈阳", "抚顺北", "清原", "梅河口", "通化", "集安"),
        c("NPR_CONV_JIAOJI", "胶济铁路", 92.0, "6小时50分钟", "济南", "淄博", "潍坊", "高密", "青岛"),
        c("NPR_CONV_LANYAN", "蓝烟铁路", 72.0, "6小时10分钟", "蓝村", "莱阳", "桃村", "烟台"),
        c("NPR_CONV_HEYANSI", "菏兖日铁路", 108.0, "10小时20分钟", "菏泽", "兖州", "曲阜", "临沂", "日照"),
        c("NPR_CONV_SHIDE", "石德铁路", 83.0, "7小时10分钟", "石家庄", "衡水", "德州"),
        c("NPR_CONV_HANDANZHANG", "邯长铁路", 96.0, "8小时20分钟", "邯郸", "武安", "长治北"),
        c("NPR_CONV_TAIJIAO", "太焦铁路", 105.0, "9小时10分钟", "太原", "榆次", "长治北", "晋城", "焦作"),
        c("NPR_CONV_HOUYUE", "侯月铁路", 118.0, "10小时35分钟", "侯马", "翼城", "长治北", "晋城", "月山"),
        c("NPR_CONV_NINGXI", "宁西铁路", 175.0, "15小时10分钟", "南京", "合肥", "六安", "固始", "潢川", "信阳", "南阳", "内乡", "西安"),
        c("NPR_CONV_WANGAN", "皖赣铁路", 125.0, "12小时30分钟", "芜湖", "宣城", "绩溪", "黄山", "景德镇", "鹰潭"),
        c("NPR_CONV_XUANHANG", "宣杭铁路", 82.0, "7小时00分钟", "宣城", "广德", "长兴", "湖州", "杭州"),
        c("NPR_CONV_YINGXIA", "鹰厦铁路", 136.0, "15小时30分钟", "鹰潭", "资溪", "光泽", "邵武", "南平", "漳平", "厦门"),
        c("NPR_CONV_ZHANGLONG", "漳龙铁路", 72.0, "8小时00分钟", "漳州", "南靖", "龙岩"),
        c("NPR_CONV_GANLONG", "赣龙铁路", 98.0, "11小时00分钟", "赣州", "于都", "瑞金", "长汀", "冠豸山", "龙岩"),
        c("NPR_CONV_HEJIU", "合九铁路", 92.0, "9小时30分钟", "合肥", "舒城", "桐城", "安庆", "九江"),
        c("NPR_CONV_QINGFU", "青阜铁路", 78.0, "8小时40分钟", "淮北", "宿州", "阜阳"),
        c("NPR_CONV_LUOZHAN", "洛湛铁路", 208.0, "20小时40分钟", "洛阳", "平顶山", "漯河", "永州", "桂林", "柳州", "玉林", "湛江"),
        c("NPR_CONV_YIZHAN", "益湛铁路", 155.0, "15小时50分钟", "益阳", "常德", "石门县北", "张家界", "永州", "贺州", "玉林", "湛江"),
        c("NPR_CONV_XIANGGUI", "湘桂铁路", 185.0, "19小时30分钟", "衡阳", "永州", "全州南", "桂林", "柳州", "南宁", "凭祥"),
        c("NPR_CONV_QIANGUI", "黔桂铁路", 126.0, "14小时10分钟", "贵阳", "都匀", "独山", "麻尾", "河池", "柳州"),
        c("NPR_CONV_NANKUN", "南昆铁路", 198.0, "21小时00分钟", "南宁", "百色", "兴义", "罗平", "石林", "昆明"),
        c("NPR_CONV_GUIKUN", "贵昆铁路", 205.0, "22小时00分钟", "贵阳", "安顺", "六盘水", "宣威", "曲靖", "昆明"),
        c("NPR_CONV_CHUANQIAN", "川黔铁路", 178.0, "19小时00分钟", "重庆", "綦江", "桐梓", "遵义", "息烽", "贵阳"),
        c("NPR_CONV_YUHUAI", "渝怀铁路", 168.0, "18小时10分钟", "重庆北", "涪陵", "武隆", "彭水", "黔江", "秀山", "怀化"),
        c("NPR_CONV_BAOCHENG", "宝成铁路", 175.0, "18小时30分钟", "宝鸡", "凤州", "略阳", "阳平关", "广元", "江油", "绵阳", "成都"),
        c("NPR_CONV_CHENGKUN", "成昆铁路", 205.0, "24小时20分钟", "成都", "峨眉", "西昌", "攀枝花", "元谋", "广通", "昆明"),
        c("NPR_CONV_YANGAN", "阳安铁路", 115.0, "11小时40分钟", "阳平关", "勉县", "汉中", "城固", "安康"),
        c("NPR_CONV_DACHENG", "达成铁路", 98.0, "10小时10分钟", "达州", "蓬安", "南充", "遂宁", "成都"),
        c("NPR_CONV_NEILIU", "内六铁路", 92.0, "10小时30分钟", "内江", "自贡", "宜宾", "昭通", "六盘水"),
        c("NPR_CONV_LANQING", "兰青铁路", 105.0, "9小时00分钟", "兰州", "海石湾", "乐都", "平安驿", "西宁"),
        c("NPR_CONV_GANWU", "干武铁路", 128.0, "12小时20分钟", "干塘", "武威", "张掖", "酒泉", "嘉峪关"),
        c("NPR_CONV_LINHA", "临哈铁路", 165.0, "16小时00分钟", "临河", "额济纳", "哈密"),
        c("NPR_CONV_NANJIANG", "南疆铁路", 220.0, "24小时30分钟", "吐鲁番", "库尔勒", "阿克苏", "喀什"),
        c("NPR_CONV_KAHE", "喀和铁路", 145.0, "15小时20分钟", "喀什", "叶城", "皮山", "和田")
    )

    private fun h(id: String, name: String, price: Double, duration: String, vararg stations: String) =
        Entry(id, name, RouteType.HIGH_SPEED, price, duration, stations.toList())

    private fun i(id: String, name: String, price: Double, duration: String, vararg stations: String) =
        Entry(id, name, RouteType.INTERCITY, price, duration, stations.toList())

    private fun c(id: String, name: String, price: Double, duration: String, vararg stations: String) =
        Entry(id, name, RouteType.CONVENTIONAL, price, duration, stations.toList())

    private fun toRoute(entry: Entry): RailwayRoute {
        val stations = entry.stationNames.mapIndexed { index, name ->
            Station(
                name = name,
                code = "${entry.id}_${index + 1}",
                city = cityForStation(name),
                network = if (entry.type == RouteType.CONVENTIONAL) StationNetwork.CONVENTIONAL else StationNetwork.HIGH_SPEED
            )
        }
        val unitPrice = entry.totalPrice / (stations.size - 1).coerceAtLeast(1)
        return RailwayRoute(
            routeId = entry.id,
            routeName = entry.name,
            stations = stations,
            segmentPrices = stations.zipWithNext().associate { (from, to) -> "${from.name}-${to.name}" to unitPrice },
            totalPrice = entry.totalPrice,
            totalDuration = entry.duration,
            routeType = entry.type
        )
    }

    private fun station(name: String, entry: Entry): Station = Station(
        name = name,
        code = entry.id + "_DIR_" + name.hashCode().toUInt().toString(16),
        city = cityForStation(name),
        network = if (entry.type == RouteType.CONVENTIONAL) StationNetwork.CONVENTIONAL else StationNetwork.HIGH_SPEED
    )

    private fun cityForStation(station: String): String {
        val normalized = station.trim()
        val aliases = linkedMapOf(
            "汉口" to "武汉", "武昌" to "武汉", "武汉" to "武汉", "北京" to "北京", "上海" to "上海",
            "天津" to "天津", "重庆" to "重庆", "广州" to "广州", "深圳" to "深圳", "成都" to "成都",
            "西安" to "西安", "郑州" to "郑州", "长沙" to "长沙", "杭州" to "杭州", "南京" to "南京",
            "合肥" to "合肥", "南昌" to "南昌", "济南" to "济南", "青岛" to "青岛", "福州" to "福州",
            "厦门" to "厦门", "南宁" to "南宁", "昆明" to "昆明", "贵阳" to "贵阳", "兰州" to "兰州",
            "西宁" to "西宁", "银川" to "银川", "乌鲁木齐" to "乌鲁木齐", "拉萨" to "拉萨",
            "呼和浩特" to "呼和浩特", "太原" to "太原", "石家庄" to "石家庄", "沈阳" to "沈阳",
            "长春" to "长春", "哈尔滨" to "哈尔滨", "宜昌" to "宜昌", "襄阳" to "襄阳",
            "张家口" to "张家口", "张家界" to "张家界", "大同" to "大同", "张掖" to "张掖",
            "宁波" to "宁波", "温州" to "温州", "金华" to "金华", "湖州" to "湖州", "绍兴" to "绍兴",
            "柳州" to "柳州", "桂林" to "桂林", "大理" to "大理", "曲靖" to "曲靖", "绵阳" to "绵阳",
            "德阳" to "德阳", "乐山" to "乐山", "宜宾" to "宜宾", "唐山" to "唐山", "秦皇岛" to "秦皇岛"
        )
        return aliases.entries.firstOrNull { normalized.startsWith(it.key) }?.value
            ?: normalized.trimEnd('东', '南', '西', '北')
    }
}
