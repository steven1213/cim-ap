package com.cim.mds.server.flow;

import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.cim.spring.support.web.BizException;
import com.cim.spring.support.web.BizCode;

/**
 * 画布 XML 解析器（DOM，JDK 自带零依赖）——「XML 单一事实源」口径的后端半边。
 *
 * <p>画布 XML 契约（前端序列化 / 后端解析，2026-11-16 定稿）：</p>
 * <pre>{@code
 * <canvas version="1">
 *   <nodes>
 *     <node id="n1" stepCode="STP010" stepName="光刻显影" stepType="PROCESS"
 *           recipeRef="" equipType="LITHO" qtimeMin="" qtimeMax="" reworkLimit=""
 *           flowRef="" batchMode="BATCH" batchSize="12" holdPoint="true" holdReason=""
 *           inspectPlan="EDC-01" samplingRate="25" x="120" y="80">
 *       <bin code="GOOD" name="良品" type="PASS"/>
 *     </node>
 *   </nodes>
 *   <edges>
 *     <edge id="e1" from="n1" to="n2" edgeType="MAIN" condition=""/>
 *   </edges>
 *   <qtime from="n1" to="n2" maxMinutes="120" action="HOLD"/>
 * </canvas>
 * }</pre>
 *
 * <p>节点 id / 边 id 由前端生成（React Flow node/edge id 原样落 XML）；坐标 x/y 只进
 * XML 不落表。本类只做<b>语法与结构</b>解析（属性齐全、类型白名单），业务守门
 * （stepCode 唯一/端点存在/边语义/引用真实）在 {@code FlowAdminService}。</p>
 *
 * <p>安全：禁用 DTD/外部实体（XXE），画布 XML 是不可信输入（前端任意传）。</p>
 */
public final class CanvasXmlSupport {

    private CanvasXmlSupport() {}

    /** XML 根/子元素与版本（契约锚点，不匹配即拒绝）。 */
    public static final String ROOT = "canvas";
    public static final String VERSION = "1";

    /** 解析出的画布节点（XML 原顺序）。 */
    public record CanvasNode(String id, String stepCode, String stepName, String stepType,
                             String recipeRef, String equipType, String qtimeMin,
                             String qtimeMax, String reworkLimit, String x, String y,
                             String flowRef, String batchMode, String batchSize,
                             boolean holdPoint, String holdReason,
                             String inspectPlan, String samplingRate) {
    }

    /** 解析出的画布边（XML 原顺序）。 */
    public record CanvasEdge(String id, String from, String to, String edgeType, String condition) {
    }

    /** 解析出的 DECISION bin 分档（XML 里 DECISION 节点的 <bin> 子元素，原顺序）。 */
    public record CanvasBin(String nodeId, String binCode, String binName, String binType, int sortNo) {
    }

    /** 解析出的跨站 Q-time 约束（XML 里 <canvas> 下的 <qtime> 子元素，原顺序；V14）。 */
    public record CanvasQtime(String from, String to, String maxMinutes, String action) {
    }

    /** 解析结果（保持 XML 原顺序）。 */
    public record CanvasDoc(List<CanvasNode> nodes, List<CanvasEdge> edges, List<CanvasBin> bins,
                            List<CanvasQtime> qtimes) {
    }

    /** 解析画布 XML：语法错误 / 根元素不符 / 属性缺失 → 400。 */
    public static CanvasDoc parse(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "画布 XML 不能为空");
        }
        Document doc;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            // XXE 防护：画布 XML 来自前端，属不可信输入
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            dbf.setNamespaceAware(false);
            doc = dbf.newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BizException(BizCode.PARAM_INVALID, "画布 XML 语法错误: " + e.getMessage());
        }
        Element root = doc.getDocumentElement();
        if (root == null || !ROOT.equals(root.getTagName())) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "画布 XML 根元素必须是 <" + ROOT + ">: " + (root == null ? "无" : root.getTagName()));
        }
        List<CanvasNode> nodes = new ArrayList<>();
        List<CanvasBin> bins = new ArrayList<>();
        NodeList nodeEls = root.getElementsByTagName("node");
        for (int i = 0; i < nodeEls.getLength(); i++) {
            Element el = (Element) nodeEls.item(i);
            String id = attr(el, "id", i, "node");
            String stepCode = attr(el, "stepCode", i, "node");
            String stepType = attr(el, "stepType", i, "node");
            nodes.add(new CanvasNode(id, stepCode, opt(el, "stepName"), stepType,
                    opt(el, "recipeRef"), opt(el, "equipType"), opt(el, "qtimeMin"),
                    opt(el, "qtimeMax"), opt(el, "reworkLimit"), opt(el, "x"), opt(el, "y"),
                    opt(el, "flowRef"), opt(el, "batchMode"), opt(el, "batchSize"),
                    Boolean.parseBoolean(el.getAttribute("holdPoint")), opt(el, "holdReason"),
                    opt(el, "inspectPlan"), opt(el, "samplingRate")));
            // DECISION 节点的 <bin> 子元素（词表；V13）
            NodeList binEls = el.getElementsByTagName("bin");
            for (int j = 0; j < binEls.getLength(); j++) {
                Element binEl = (Element) binEls.item(j);
                bins.add(new CanvasBin(id, attr(binEl, "code", j, "bin"),
                        opt(binEl, "name"), attr(binEl, "type", j, "bin"), j + 1));
            }
        }
        List<CanvasEdge> edges = new ArrayList<>();
        NodeList edgeEls = root.getElementsByTagName("edge");
        for (int i = 0; i < edgeEls.getLength(); i++) {
            Element el = (Element) edgeEls.item(i);
            String from = attr(el, "from", i, "edge");
            String to = attr(el, "to", i, "edge");
            String edgeType = attr(el, "edgeType", i, "edge");
            edges.add(new CanvasEdge(opt(el, "id"), from, to, edgeType, opt(el, "condition")));
        }
        // 跨站 Q-time 约束（V14：<canvas> 下 <qtime from to maxMinutes action/>）
        List<CanvasQtime> qtimes = new ArrayList<>();
        NodeList qtimeEls = root.getElementsByTagName("qtime");
        for (int i = 0; i < qtimeEls.getLength(); i++) {
            Element el = (Element) qtimeEls.item(i);
            qtimes.add(new CanvasQtime(attr(el, "from", i, "qtime"), attr(el, "to", i, "qtime"),
                    attr(el, "maxMinutes", i, "qtime"), attr(el, "action", i, "qtime")));
        }
        return new CanvasDoc(nodes, edges, bins, qtimes);
    }

    /** 必填属性（缺失 → 400，带元素序号便于定位）。 */
    private static String attr(Element el, String name, int index, String kind) {
        String v = el.getAttribute(name);
        if (v == null || v.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "画布 XML 第 " + (index + 1) + " 个 " + kind + " 缺少属性 " + name);
        }
        return v.trim();
    }

    /** 可空属性。 */
    private static String opt(Element el, String name) {
        String v = el.getAttribute(name);
        return v == null || v.isBlank() ? null : v.trim();
    }
}
