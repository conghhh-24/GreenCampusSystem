"""Render the ER diagram from a read-only information_schema snapshot."""
import html
import json
import math
from pathlib import Path
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
DATA = json.loads((ROOT / 'er-schema.json').read_text(encoding='utf-8'))
W, H, SCALE = 3380, 2180, 2
FONT = 'C:/Windows/Fonts/msyh.ttc'
BOLD = 'C:/Windows/Fonts/msyhbd.ttc'
TABLES = {
    'users': ('用户', 100, 300, '#245A81'),
    'categories': ('物品类别', 100, 950, '#245A81'),
    'items': ('闲置物品', 950, 300, '#286F60'),
    'trade_orders': ('交易订单', 1800, 300, '#766043'),
    'trade_feedback': ('交易评价', 2650, 300, '#766043'),
    'recycling_stations': ('回收点', 2650, 950, '#286F60'),
    'recycling_orders': ('回收预约', 950, 1320, '#286F60'),
    'recycling_records': ('回收完成记录', 1800, 1320, '#286F60'),
    'eco_point_records': ('环保积分流水', 2650, 1320, '#67528A'),
    'reputation_records': ('信誉积分流水', 100, 1470, '#67528A'),
}
COLS = {t: [c for c in DATA['columns'] if c['TABLE_NAME'] == t] for t in TABLES}
BOXES = {t: (v[1], v[2], 620, 104 + 28 * len(COLS[t])) for t, v in TABLES.items()}
FK = {(f['TABLE_NAME'], f['COLUMN_NAME']): f for f in DATA['foreign_keys']}
UNIQUE = set()
for t in TABLES:
    indexes = {}
    for idx in DATA['indexes']:
        if idx['TABLE_NAME'] == t and idx['NON_UNIQUE'] == '0':
            indexes.setdefault(idx['INDEX_NAME'], []).append(idx['COLUMN_NAME'])
    UNIQUE.update((t, columns[0]) for name, columns in indexes.items() if name != 'PRIMARY' and len(columns) == 1)

# Each route stays in the space between tables. Source = parent, target = child.
REL = [
 ('items','owner_id','发布',[(720,370),(950,370)],(830,350)),
 ('items','reviewed_by','审核',[(720,500),(950,500)],(830,480)),
 ('items','category_id','物品分类',[(720,1030),(880,1030),(880,840),(950,840)],(855,960)),
 ('trade_orders','item_id','物品交易',[(1570,390),(1800,390)],(1685,370)),
 ('trade_orders','buyer_id','作为买家',[(300,300),(300,200),(1930,200),(1930,300)],(1470,180)),
 ('trade_orders','seller_id','作为卖家',[(540,300),(540,245),(2210,245),(2210,300)],(1470,225)),
 ('trade_feedback','buyer_id','提交评价',[(150,300),(150,140),(2800,140),(2800,300)],(2180,120)),
 ('trade_feedback','trade_id','订单评价',[(2420,430),(2650,430)],(2535,410)),
 ('reputation_records','user_id','信誉变更',[(250,712),(250,800),(60,800),(60,1530),(100,1530)],(180,780)),
 ('recycling_orders','applicant_id','申请回收',[(720,650),(790,650),(790,1380),(950,1380)],(850,1300)),
 ('recycling_orders','item_id','物品预约',[(1200,908),(1200,1320)],(1290,1100)),
 ('recycling_orders','station_id','承接预约',[(2960,1166),(2960,1230),(1400,1230),(1400,1320)],(2160,1210)),
 ('recycling_records','recycling_order_id','生成记录',[(1570,1430),(1800,1430)],(1685,1410)),
 ('recycling_records','category_id','回收分类',[(720,1100),(845,1100),(845,1740),(1950,1740),(1950,1592)],(1500,1720)),
 ('eco_point_records','recycling_record_id','积分来源',[(2420,1470),(2650,1470)],(2535,1450)),
 ('eco_point_records','user_id','环保积分',[(500,712),(500,850),(750,850),(750,1800),(2990,1800),(2990,1592)],(2320,1780)),
]
assert {(t,c) for t,c,*_ in REL} == set(FK), 'Every physical foreign key must appear once.'
assert len(DATA['columns']) == sum(map(len, COLS.values()))
REMOVED = {'original_price','estimated_recycle_price','condition_level','recycle_amount','accepted_categories'}
assert not any(c['COLUMN_NAME'] in REMOVED or (c['TABLE_NAME']=='recycling_stations' and c['COLUMN_NAME']=='phone') for c in DATA['columns'])

im = Image.new('RGB', (W*SCALE,H*SCALE), 'white')
draw = ImageDraw.Draw(im)
svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">', '<rect width="100%" height="100%" fill="white"/>']
mxfile = ET.Element('mxfile', host='app.diagrams.net')
diagram = ET.SubElement(mxfile,'diagram',name='逻辑E-R图（实际数据库）')
model = ET.SubElement(diagram,'mxGraphModel',dx=str(W),dy=str(H),grid='1',gridSize='10',page='1',pageWidth=str(W),pageHeight=str(H))
mxroot = ET.SubElement(model,'root')
ET.SubElement(mxroot,'mxCell',id='0')
ET.SubElement(mxroot,'mxCell',id='1',parent='0')
counter = 0
fonts = {}

def font(size, bold=False):
    key = size,bold
    if key not in fonts: fonts[key]=ImageFont.truetype(BOLD if bold else FONT, round(size*SCALE))
    return fonts[key]

def cell(value,x,y,w,h,style,ident=None,parent='1'):
    global counter
    counter+=1
    node=ET.SubElement(mxroot,'mxCell',id=ident or f'shape{counter}',value=value,style=style,vertex='1',parent=parent)
    ET.SubElement(node,'mxGeometry',x=str(x),y=str(y),width=str(w),height=str(h),attrib={'as':'geometry'})
    return node

def rect(x,y,w,h,fill,stroke=None,r=0):
    xy=(x*SCALE,y*SCALE,(x+w)*SCALE,(y+h)*SCALE)
    draw.rounded_rectangle(xy,r*SCALE,fill=fill,outline=stroke,width=SCALE) if r else draw.rectangle(xy,fill=fill,outline=stroke,width=SCALE)
    svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}" stroke="{stroke or "none"}"/>')

def text(value,x,y,size=20,color='#233648',bold=False,align='left',editable=True):
    f=font(size,bold)
    width=draw.textlength(value,font=f)/SCALE
    left=x-width/2 if align=='center' else x-width if align=='right' else x
    draw.text((left*SCALE,(y-size)*SCALE),value,font=f,fill=color)
    svg.append(f'<text x="{x}" y="{y-3}" font-family="Microsoft YaHei, sans-serif" font-size="{size}" font-weight="{700 if bold else 400}" fill="{color}" text-anchor="{dict(left="start",center="middle",right="end")[align]}">{html.escape(value)}</text>')
    if editable:
        cell(value,left,y-size-4,width+5,size+10,f'text;html=0;align=left;verticalAlign=middle;whiteSpace=nowrap;fontFamily=Microsoft YaHei;fontSize={size};fontColor={color};fontStyle={1 if bold else 0};')
    return width

def label(value,x,y,size=18,color='#42586D'):
    width=draw.textlength(value,font=font(size))/SCALE
    rect(x-width/2-6,y-size-4,width+12,size+10,'#FFFFFF')
    text(value,x,y,size,color,align='center',editable=False)
    cell(value,x-width/2-6,y-size-4,width+12,size+10,f'text;html=0;align=center;verticalAlign=middle;whiteSpace=nowrap;fontFamily=Microsoft YaHei;fontSize={size};fontColor={color};fillColor=#FFFFFF;')

def polyline(points,color='#8997A5'):
    draw.line([(x*SCALE,y*SCALE) for x,y in points],fill=color,width=2*SCALE,joint='curve')
    svg.append(f'<polyline points="{" ".join(str(x)+","+str(y) for x,y in points)}" fill="none" stroke="{color}" stroke-width="2" stroke-linejoin="round"/>')

def range_mark(value,p,nextp):
    dx,dy=nextp[0]-p[0],nextp[1]-p[1]
    length=math.hypot(dx,dy)
    ux,uy=dx/length,dy/length
    # Numeric multiplicities avoid ambiguous arrows and keep nullable FKs explicit.
    tx,ty=p[0]+ux*32,p[1]+uy*30
    if ux:ty-=10
    else:tx+=30
    label(value,tx,ty,16)

text('校园闲置物品交易与环保回收管理系统  逻辑 E-R 图',100,62,35,bold=True)
text('依据 campus_recycle 实际结构 · 2026-11-02 · 10 张业务表 / 82 个字段 / 16 条外键',100,102,21,color='#607182')

# Draw connectors before tables so no connector covers field text.
for i,(child,column,name,points,pos) in enumerate(REL,1):
    parent=FK[(child,column)]['REFERENCED_TABLE_NAME']
    col=next(c for c in COLS[child] if c['COLUMN_NAME']==column)
    prange='0..1' if col['IS_NULLABLE']=='YES' else '1'
    crange='0..1' if (child,column) in UNIQUE else '0..N'
    polyline(points)
    edge=ET.SubElement(mxroot,'mxCell',id=f'edge{i}',value='',edge='1',parent='1',source=parent,target=child,
        style='edgeStyle=none;rounded=0;html=0;startArrow=none;endArrow=none;strokeColor=#8997A5;strokeWidth=2;')
    geo=ET.SubElement(edge,'mxGeometry',relative='1',attrib={'as':'geometry'})
    arr=ET.SubElement(geo,'Array',attrib={'as':'points'})
    for px,py in points[1:-1]:ET.SubElement(arr,'mxPoint',x=str(px),y=str(py))
    sx,sy,sw,sh=BOXES[parent];tx,ty,tw,th=BOXES[child]
    edge.set('style',edge.get('style')+f'exitX={(points[0][0]-sx)/sw};exitY={(points[0][1]-sy)/sh};entryX={(points[-1][0]-tx)/tw};entryY={(points[-1][1]-ty)/th};exitPerimeter=0;entryPerimeter=0;')

for table,(cn,x,y,color) in TABLES.items():
    h=BOXES[table][3]
    rect(x,y,620,h,'#FFFFFF','#B8C5D0',8)
    rect(x,y,620,74,color)
    text(cn,x+18,y+30,23,'#FFFFFF',True,editable=False)
    text(table,x+18,y+59,22,'#FFFFFF',editable=False)
    # All fields are editable as ordinary text in a single table vertex.
    header=f'<b>{html.escape(cn)}</b><br/>{table}'
    rows=[]
    rect(x+1,y+74,618,30,'#EEF2F5')
    text('字段',x+16,y+97,17,'#607182',editable=False)
    text('类型',x+323,y+97,17,'#607182',editable=False)
    text('约束',x+512,y+97,17,'#607182',editable=False)
    for n,c in enumerate(COLS[table]):
        top=y+104+n*28
        if n%2==0:rect(x+1,top,618,28,'#F6F8FA')
        legacy=c['COLUMN_NAME']=='meeting_day'
        dtype='ENUM' if c['COLUMN_TYPE'].startswith('enum(') else c['COLUMN_TYPE'].upper()
        badges=[]
        if c['COLUMN_KEY']=='PRI':badges.append('PK')
        if (table,c['COLUMN_NAME']) in FK:badges.append('FK')
        if (table,c['COLUMN_NAME']) in UNIQUE:badges.append('UQ')
        if c['IS_NULLABLE']=='YES':badges.append('?')
        key=' '.join(badges)
        value=c['COLUMN_NAME']+(' †' if legacy else '')
        ink='#8B6E4C' if legacy else '#233648'
        assert draw.textlength(value,font=font(19))/SCALE<295, value
        assert draw.textlength(dtype,font=font(17))/SCALE<182, dtype
        text(value,x+16,top+22,19,ink,c['COLUMN_KEY']=='PRI',editable=False)
        text(dtype,x+323,top+22,17,'#607182',editable=False)
        text(key,x+512,top+22,17,'#286F60',editable=False)
        rows.append(f'<tr><td width="300">{html.escape(value)}</td><td width="185">{dtype}</td><td>{key}</td></tr>')
    contents=f'<div style="background:{color};color:white;padding:10px;font-size:22px;">{header}</div><table style="width:100%;font-size:19px;line-height:28px;border-collapse:collapse;"><tr style="height:30px;background:#EEF2F5;color:#607182;"><td>字段</td><td>类型</td><td>约束</td></tr>'+''.join(rows)+'</table>'
    cell(contents,x,y,620,h,'shape=rectangle;rounded=1;whiteSpace=wrap;html=1;align=left;verticalAlign=top;spacing=0;fillColor=#FFFFFF;strokeColor=#B8C5D0;fontFamily=Microsoft YaHei;',ident=table)

# Multiplicities and short relation names are intentionally outside entity boxes.
for i,(child,column,name,points,pos) in enumerate(REL,1):
    col=next(c for c in COLS[child] if c['COLUMN_NAME']==column)
    range_mark('0..1' if col['IS_NULLABLE']=='YES' else '1',points[0],points[1])
    range_mark('0..1' if (child,column) in UNIQUE else '0..N',points[-1],points[-2])
    label(f'{i:02d} {name}',*pos,18)

rect(100,1850,3170,248,'#F4F7FA',r=10)
text('读图说明',125,1886,22,bold=True)
text('PK 主键  /  FK 外键  /  UQ 单列唯一  /  ? 允许为空  /  1 恰好一个  /  0..1 至多一个  /  0..N 零个到多个',125,1926,20)
text('连线两端的数字表示该端记录数。例如：物品 1 — 0..N 交易订单，表示一个物品可保留多次申请或取消的历史订单。',125,1964,20)
text('† meeting_day 是仍存在的旧星期字段，只兼容历史记录；当前发布和新订单使用 meeting_date（DATE）。',125,2002,20)
text('reputation_records.source_id 是业务来源编号，没有物理外键；备份表不属于业务实体，因此不入图。ENUM 完整取值见字段清单。',125,2040,20)
text('唯一外键决定 0..1：回收预约.item_id、完成记录.recycling_order_id、交易评价.trade_id。可空外键按数据库实际结构标注。',125,2078,20)
text('实体、字段和关系均从 information_schema 核对；本图不包含密码、用户数据或数据库连接凭据。',100,2140,18,color='#607182')

(ROOT/'er-diagram.svg').write_text('\n'.join(svg+['</svg>']),encoding='utf-8')
im.save(ROOT/'er-diagram.png',optimize=True)
im.resize((1690,1090),Image.Resampling.LANCZOS).save(ROOT/'er-diagram-preview.png',optimize=True)
ET.indent(mxfile)
ET.ElementTree(mxfile).write(ROOT/'er-diagram.drawio',encoding='utf-8',xml_declaration=True)

# Generate the corresponding Mermaid model and field dictionary from the same snapshot.
names={t:spec[0] for t,spec in TABLES.items()}
md=['# E-R 图','', '本图依据当前 MySQL `campus_recycle` 的实际结构核对生成：10张业务表、82个字段、16条物理外键。已删除的6个字段不在图中；备份表不作为业务实体。','',
    '![当前数据库逻辑E-R图](report/er-diagram.png)','',
    '高清图：[PNG](report/er-diagram.png) · [SVG](report/er-diagram.svg) · [可编辑 draw.io](report/er-diagram.drawio)','',
    '## 关系与基数','',
    '`PK` 为主键，`FK` 为外键，`UQ` 为单列唯一，`?` 表示可空。连线端点标注该端记录数；0..N表示零到多条。图中两处 `meeting_day` 为真实存在的历史兼容字段，新业务使用 `meeting_date DATE`。','',
    '| 关联父表 | 子表及外键 | 父端基数 | 子端基数 | 含义 |','| --- | --- | --- | --- | --- |']
mermaid=['erDiagram']
for child,column,name,_,_ in REL:
    parent=FK[(child,column)]['REFERENCED_TABLE_NAME']
    col=next(c for c in COLS[child] if c['COLUMN_NAME']==column)
    prange='0..1' if col['IS_NULLABLE']=='YES' else '1'
    crange='0..1' if (child,column) in UNIQUE else '0..N'
    md.append(f'| `{parent}` | `{child}.{column}` | {prange} | {crange} | {name} |')
    mermaid.append(f'    {parent} '+('|o' if prange=='0..1' else '||')+'--'+('o|' if crange=='0..1' else 'o{')+f' {child} : "{name}"')
for table in TABLES:
    mermaid.append(f'    {table} {{')
    for c in COLS[table]:
        dt='enum' if c['COLUMN_TYPE'].startswith('enum(') else c['COLUMN_TYPE'].replace(',','_')
        badges=[]
        if c['COLUMN_KEY']=='PRI':badges.append('PK')
        if (table,c['COLUMN_NAME']) in FK:badges.append('FK')
        if (table,c['COLUMN_NAME']) in UNIQUE:badges.append('UK')
        note='旧星期兼容字段' if c['COLUMN_NAME']=='meeting_day' else '可空' if c['IS_NULLABLE']=='YES' else '非空'
        mermaid.append(f'        {dt} {c["COLUMN_NAME"]} '+','.join(badges)+f' "{note}"')
    mermaid.append('    }')
md+=['','## Mermaid 源图','','```mermaid',*mermaid,'```','',
    '## 与业务流程的区别','',
    '- 物品与交易订单在数据库中是1对多，申请取消后可保留历史订单；业务代码只允许当前有效申请占用物品。',
    '- 物品与回收预约、回收预约与完成记录、交易订单与评价，因子表外键有唯一约束，均为1对0..1。',
    '- 环保积分流水的 `recycling_record_id` 可空且没有唯一约束，因此数据库是可选关联的一对多；不能因为当前完成流程只写一条流水就画成一对一。',
    '- `reviewed_by` 关联用户表，由后端保证该用户是管理员；管理员是用户角色，不单建管理员实体。',
    '- `source_id` 可来源于不同业务，没有统一外键，不画成物理关联。',
    '- 类别和交易评价有表与后端用途，但页面入口仍不完整；E-R图表达数据库结构，并不表示全部页面功能已实现。','']
(ROOT.parent/'03-ER图.md').write_text('\n'.join(md),encoding='utf-8')
logic=['# 数据库逻辑结构','', '以下为当前数据库的10张业务表。字段类型、可空、默认值、主键、唯一约束及外键来自实际 `information_schema`，包括尚保留的旧星期字段。','']
for table in TABLES:
    logic += [f'## {names[table]} `{table}`','', '| 字段 | 类型 | 可空 | 约束/关联 | 默认值 |','| --- | --- | --- | --- | --- |']
    for c in COLS[table]:
        badges=[]
        if c['COLUMN_KEY']=='PRI':badges.append('PK，自增' if 'auto_increment' in c['EXTRA'] else 'PK')
        if (table,c['COLUMN_NAME']) in UNIQUE:badges.append('UNIQUE')
        if (table,c['COLUMN_NAME']) in FK:
            f=FK[(table,c['COLUMN_NAME'])];badges.append(f'FK → {f["REFERENCED_TABLE_NAME"]}.{f["REFERENCED_COLUMN_NAME"]}')
        if c['COLUMN_NAME']=='meeting_day':badges.append('旧记录兼容，不用于新发布')
        if table=='reputation_records' and c['COLUMN_NAME']=='source_id':badges.append('业务来源编号，非外键')
        default='—' if c['COLUMN_DEFAULT']=='NULL' else '`'+c['COLUMN_DEFAULT']+'`'
        logic.append(f'| `{c["COLUMN_NAME"]}` | `{c["COLUMN_TYPE"]}` | {"是" if c["IS_NULLABLE"]=="YES" else "否"} | {"；".join(badges) or "—"} | {default} |')
    logic.append('')
logic+=['## 结构说明','', '旧数据库保留 `items.meeting_day` 和 `trade_orders.meeting_day`，新建库脚本不再创建这两列。当前业务使用 `meeting_date`。备份表仅存档删除字段值，不参与业务关系。','', '索引中的普通MUL标识不代表唯一。图中基数根据单列唯一索引与外键可空性确定。','']
(ROOT.parent/'04-数据库逻辑结构.md').write_text('\n'.join(logic),encoding='utf-8')
print('Generated PNG, SVG, draw.io, Mermaid and field dictionary: 10 entities, 82 fields, 16 foreign keys.')
