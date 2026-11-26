package cc.samlab.ticket.ui.sale

private val announcementStations = listOf(
    SaleStation("马鞍山南", "masn", "maanshannan", "上局", "马鞍山"),
    SaleStation("郑蒲港", "zpg", "zhengpugang", "上局", "马鞍山"),
    SaleStation("含山", "hs", "hanshan", "上局", "马鞍山"),
    SaleStation("利辛", "lx", "lixin", "上局", "亳州"),
    SaleStation("蒙城", "mc", "mengcheng", "上局", "亳州"),
    SaleStation("颍泉北", "yqb", "yingquanbei", "上局", "阜阳"),
    SaleStation("固镇南", "gzn", "guzhennan", "上局", "蚌埠"),
    SaleStation("宿州西", "szx", "suzhouxi", "上局", "宿州"),
    SaleStation("双堆集", "sdj", "shuangduiji", "上局", "淮北"),
    SaleStation("淮北西", "hbx", "huaibeixi", "上局", "淮北"),
)

internal fun supplementSaleStations(
    directory: List<SaleStation>,
    scheduledNames: Set<String>,
): List<SaleStation> {
    val directoryNames = directory.mapTo(hashSetOf()) { it.name }
    val additions = announcementStations.filter {
        it.name in scheduledNames && it.name !in directoryNames
    }
    return additions + directory
}
