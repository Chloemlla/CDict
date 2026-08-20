package com.chloemlla.cdict.ui.about

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.ui.graphics.vector.ImageVector

data class WhatsNewSlide(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val bullets: List<String>,
    val tip: String? = null,
)

object WhatsNewData {
    fun slides(): List<WhatsNewSlide> = listOf(
        WhatsNewSlide(
            icon = Icons.Filled.NewReleases,
            title = "官方客户端拥有独立请求额度",
            subtitle = "联网请求现在可以识别官方客户端并独立计数，共享校园网或运营商出口时，不再轻易与其他用户挤占同一个额度。",
            bullets = listOf(
                "独立但不无限：官方客户端获得更宽松的请求额度，同时保留必要的使用上限。",
                "保护隐私：仅使用随机生成的安装标识区分额度，不读取硬件标识，也不关联账号或手机号。",
                "兼容旧版本：旧版客户端仍可继续使用联网功能。",
            ),
            tip = "在线翻译、语言列表、朗读与赞赏页均会自动使用新的请求额度。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.NewReleases,
            title = "署名可以直接在应用内申请，提交成功撒 🎉",
            subtitle = "赞赏支持页的署名说明改成了带要点的卡片，下面新增「申请加入鸣谢名单」表单：填交易号和想展示的称呼即可提交，开发者核实后加入名单；名单同时展示在开源许可声明页。",
            bullets = listOf(
                "不写备注也能署名：转账时忘了写备注的，用表单填交易号（账单详情里的交易单号，6-64 位）与称呼提交即可，核实后加入名单。",
                "请求正文仍只有两项：交易号与称呼；联网请求附带的随机安装标识只用于区分请求额度，不读取硬件标识。同一交易号重复提交不会重复排队。",
                "本地限流防误触：两次提交至少间隔 30 秒、一小时内最多 5 次，超出时应用内直接提示，不会白白发请求（服务端另有独立限流）。",
                "提交成功会有 🎉 从屏幕上洒落一遍，动画不拦触摸，也不会读给读屏软件。",
                "名单两处可见：赞赏支持页底部与「开源许可声明」页的「赞赏鸣谢名单」分区都展示，名字改成一个个圆角标签，长名字自动换行。",
            ),
            tip = "入口：关于页 → 赞赏支持 → 页面底部「申请加入鸣谢名单」。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.NewReleases,
            title = "赞赏页新增鸣谢名单，收款码按填写的地址原样下发",
            subtitle = "赞赏支持页新增「鸣谢名单」：转账备注里写上想展示的称呼，开发者核实后加入名单，应用内即时可见。收款码图片改为直接指向运营方填写的图床地址，服务端不再下载和转存图片。",
            bullets = listOf(
                "署名鸣谢：两端（支付宝 / 微信）赞赏都支持署名，名字在开发者核实转账备注后加入服务端名单，赞赏页的鸣谢名单随即实时更新，不需要更新应用；不写备注就是匿名支持。",
                "图片地址原样生效：请求收款码时，服务端返回 302 跳到后台填写的图床地址本身，不再把图片下载一遍再转发；后台改地址立刻生效。",
                "取图会直连该图床：因此取收款码这一跳不再只连自有后端，该图床能看到你的 IP 与 UA（与在浏览器里打开一张图片相同）；其余联网功能仍然只连 tts.chloemlla.com。",
                "仍不内置收款信息：安装包里没有任何收款码、收款账号或图床地址，赞赏页每次打开都实时向自有后端拉取渠道、文案与名单。",
                "赞赏仍不解锁任何东西：应用永久免费，署名与赞赏都不影响任何功能与使用体验。",
            ),
            tip = "入口：关于页 → 赞赏支持 → 页面底部「鸣谢名单」。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.NewReleases,
            title = "伙伴应用可一键下载",
            subtitle = "关于页「伙伴应用」分区新增下载入口：网络代理伙伴应用 Clash Meta for Android 属于可选安装，装或不装都不影响词典与背词。",
            bullets = listOf(
                "可选安装：未安装时条目显示为「下载 Clash Meta for Android（可选）」，点击直接前往其 GitHub Releases 最新版页面；长按可复制下载链接。",
                "已装即更新：检测到已安装时条目改为「更新 Clash Meta for Android」，同样指向最新发布版本。",
                "状态文案更清楚：未安装时的说明改为「未安装 Clash Meta for Android（可选），联网请求直连」，避免被误读成缺少必需组件。",
                "伙伴身份已打通：Clash 侧改为按发布签名证书摘要认可伙伴，装好并运行后关于页即可读到内核与隧道状态，不再停留在「读不到伙伴状态」。",
            ),
            tip = "入口：关于页 → 伙伴应用 → 下载 / 更新 Clash Meta for Android。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.NewReleases,
            title = "收款码可存相册",
            subtitle = "赞赏支持页的两张收款码下方各新增「保存到相册」按钮，存好后可在微信或支付宝里直接从相册选图扫码，不必再截图裁剪。",
            bullets = listOf(
                "一键保存：图片加载完成后按钮才出现，点一下把当前显示的收款码原样写入相册的 Pictures/CDict 相册，文件名形如 CDict-alipay-20260820-120000.png。",
                "两张分开保存：支付宝与微信各有独立按钮，按钮上直接标出渠道名，保存中会显示「正在保存…」并禁用重复点击，结束后用提示条告知成功或失败。",
                "Android 10 及以上不需要任何权限；Android 9 及以下系统写相册必须走旧版存储权限，只在你首次点保存时申请，拒绝则只是保存不了，其他功能不受影响。",
                "保存的是本次从服务端取到的图片，不内置在安装包里；换码后重新打开赞赏页保存即可拿到新的收款码。",
            ),
            tip = "入口：关于页 → 赞赏支持 → 收款码下方「保存…收款码到相册」。",
        ),
    )
}