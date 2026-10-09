package com.cim.iam.server.common;

/**
 * 数据来源（同树混源隔离依据，见 docs/business/iam-ap/server/identity-directory.md §1）。
 *
 * <p>身份目录采用「同树不同源」：行政组织与人员来自 AD（只读同步），
 * 制造组织（厂区→车间→产线→工序→责任区）与非 AD 人员（设备厂商 / 服务账号）由 IAM 自建。
 * 同步器<b>只写自己那一份</b>（{@link #AD_SYNCED}），绝不覆盖 IAM 自建数据——这是同树不打架的关键。</p>
 */
public enum DataOrigin {

    /** 由 AD/LDAP 只读同步而来；IAM 侧只读，不接受本地编辑。 */
    AD_SYNCED,

    /** IAM 自建（制造组织 / 非 AD 人员 / 设备厂商 / 服务账号）。 */
    IAM_MANAGED
}
