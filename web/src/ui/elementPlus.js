import {
    ElAlert, ElAside, ElAutocomplete, ElAvatar, ElBacktop,
    ElButton, ElCard, ElCheckbox, ElCheckboxGroup, ElCol,
    ElContainer, ElDatePicker, ElDialog, ElEmpty, ElForm,
    ElFormItem, ElHeader, ElIcon, ElInput, ElInputNumber,
    ElInputTag, ElMain, ElOption, ElPagination, ElRow,
    ElSelect, ElSlider, ElSpace, ElSwitch, ElTabs, ElTabPane, ElTable,
    ElTableColumn, ElTableV2, ElTag, ElText, ElTimePicker,
    ElTooltip, ElLoading,
} from 'element-plus'

// Register the components this application uses, without the entire plugin.
const components = [
    ElAlert, ElAside, ElAutocomplete, ElAvatar, ElBacktop,
    ElButton, ElCard, ElCheckbox, ElCheckboxGroup, ElCol,
    ElContainer, ElDatePicker, ElDialog, ElEmpty, ElForm,
    ElFormItem, ElHeader, ElIcon, ElInput, ElInputNumber,
    ElInputTag, ElMain, ElOption, ElPagination, ElRow,
    ElSelect, ElSlider, ElSpace, ElSwitch, ElTabs, ElTabPane, ElTable,
    ElTableColumn, ElTableV2, ElTag, ElText, ElTimePicker,
    ElTooltip,
]

export default {
    install(app) {
        for (const component of components) app.use(component)
        app.use(ElLoading)
    },
}
