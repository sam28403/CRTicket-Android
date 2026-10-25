import { native } from './platform.js'

const fallback = {
  read_failed: '无法读取个人资料', arrival_unsupported: '当前环境不支持到站检测',
  save_failed: '保存失败', saved: '已保存', arrival_failed: '无法更新到站检测', arrival_updated: '已更新到站检测',
  export_empty: '暂无可导出的车票', export_sheet: '车票',
  export_filename: 'CRTicket-%1$s.xlsx', backup_filename: 'CRTicket-backup-%1$s.json',
  export_size_limit: '导出文件不能超过28MB', export_read_failed: '无法读取导出文件',
  file_save_failed: '文件保存失败', unknown_action: '未知操作',
  file_saved: '文件已保存', file_cancelled: '已取消文件选择', operation_failed: '操作失败',
  backup_no_tickets: '备份文件中没有找到车票数据', backup_limit: '一次最多导入10000条车票',
  import_failed: '导入失败', import_complete: '导入完成：新增 %1$d 条，略过 %2$d 条，未处理 %3$d 条',
}

export function settingsText(key, ...args) {
  const strings = native ? JSON.parse(native.settingsStrings()) : fallback
  return (strings[key] || key).replace(/%(\d+)\$[sd]/g, (_, index) => String(args[Number(index) - 1]))
}
