"""生成 M3-3 的测试素材：一份中文 DOCX 和一份 ASCII PDF。"""
import os

BASE = os.path.dirname(os.path.abspath(__file__))

# ---------------- DOCX（中文） ----------------
from docx import Document

doc = Document()
doc.add_heading("李四", level=1)
doc.add_paragraph("Python / 数据平台工程师 | 4 年经验")
doc.add_paragraph("邮箱：lisi@example.com")

doc.add_heading("技能", level=2)
for s in ["Python、SQL、Shell", "Airflow、Kafka、ClickHouse", "Docker、Kubernetes"]:
    doc.add_paragraph(s, style="List Bullet")

doc.add_heading("工作经历", level=2)
doc.add_paragraph(
    "2021.07 - 至今  某数据公司  数据平台工程师\n"
    "负责离线数仓建设，日均处理 3TB 数据；\n"
    "搭建 Airflow 调度平台，任务成功率从 92% 提升到 99.6%。"
)

doc.add_heading("教育背景", level=2)
doc.add_paragraph("2017.09 - 2021.06  某大学  软件工程  本科")

docx_path = os.path.join(BASE, "sample-resume.docx")
doc.save(docx_path)


# ---------------- PDF（ASCII，手工构造但 xref 偏移精确） ----------------
def make_pdf(path, lines):
    content = ["BT", "/F1 12 Tf", "72 720 Td", "16 TL"]
    for line in lines:
        esc = line.replace("\\", r"\\").replace("(", r"\(").replace(")", r"\)")
        content.append(f"({esc}) Tj")
        content.append("T*")
    content.append("ET")
    stream = "\n".join(content).encode("latin-1")

    objs = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
        b"/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
        b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    ]

    out = bytearray(b"%PDF-1.4\n")
    offsets = []
    for i, obj in enumerate(objs, start=1):
        offsets.append(len(out))
        out += f"{i} 0 obj\n".encode("latin-1") + obj + b"\nendobj\n"

    xref_pos = len(out)
    out += f"xref\n0 {len(objs) + 1}\n".encode("latin-1")
    out += b"0000000000 65535 f \n"
    for off in offsets:
        out += f"{off:010d} 00000 n \n".encode("latin-1")
    out += (
        f"trailer\n<< /Size {len(objs) + 1} /Root 1 0 R >>\n"
        f"startxref\n{xref_pos}\n%%EOF\n"
    ).encode("latin-1")

    with open(path, "wb") as f:
        f.write(bytes(out))


pdf_path = os.path.join(BASE, "sample-resume.pdf")
make_pdf(
    pdf_path,
    [
        "WANG WU",
        "Java Backend Engineer | 6 years experience",
        "Email: wangwu@example.com",
        "",
        "SKILLS",
        "- Java 17/21, Spring Boot, MyBatis",
        "- MySQL, Redis, Kafka, Elasticsearch",
        "- Docker, Kubernetes, Jenkins",
        "",
        "EXPERIENCE",
        "2020.03 - now   E-commerce Co.   Java Engineer",
        "- Rebuilt order service into 4 microservices, p99 latency 320ms -> 90ms",
        "- Built async task framework on Redis Stream, 2M orders per day",
        "",
        "EDUCATION",
        "2014.09 - 2018.06   Some University   Computer Science   Bachelor",
    ],
)

print("docx:", docx_path, os.path.getsize(docx_path), "bytes")
print("pdf :", pdf_path, os.path.getsize(pdf_path), "bytes")
