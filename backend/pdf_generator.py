import os
from reportlab.lib.pagesizes import letter
from reportlab.lib import colors
from reportlab.lib.units import inch
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, KeepTogether
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle

def create_pdf(data: dict, filename: str):
    """
    Generates a professional ReturnRadar audit report PDF.
    
    data format:
    {
        "total_leak": float,
        "highest_bleeding_category": str,
        "number_of_returns": int,
        "category_breakdown": [
            {"category": str, "percentage": float, "total_value": float},
            ...
        ],
        "worst_performing_skus": [
            {"sku": str, "product_name": str, "return_rate": float, "insight": str, "action": str},
            ...
        ]
    }
    """
    doc = SimpleDocTemplate(
        filename,
        pagesize=letter,
        rightMargin=40,
        leftMargin=40,
        topMargin=40,
        bottomMargin=40
    )
    
    styles = getSampleStyleSheet()
    
    # Custom elegant styles
    primary_color = colors.HexColor("#0F172A")  # Slate 900
    accent_color = colors.HexColor("#3B82F6")   # Blue 500
    text_color = colors.HexColor("#334155")     # Slate 700
    bg_light = colors.HexColor("#F8FAFC")       # Slate 50
    
    title_style = ParagraphStyle(
        'DocTitle',
        parent=styles['Heading1'],
        fontSize=24,
        leading=28,
        textColor=primary_color,
        spaceAfter=6
    )
    
    subtitle_style = ParagraphStyle(
        'DocSubTitle',
        parent=styles['Normal'],
        fontSize=10,
        leading=14,
        textColor=colors.HexColor("#64748B"),
        spaceAfter=20
    )
    
    heading2_style = ParagraphStyle(
        'SectionHeader',
        parent=styles['Heading2'],
        fontSize=14,
        leading=18,
        textColor=primary_color,
        spaceBefore=12,
        spaceAfter=8,
        keepWithNext=True
    )
    
    body_style = ParagraphStyle(
        'BodyTextCustom',
        parent=styles['Normal'],
        fontSize=10,
        leading=14,
        textColor=text_color
    )
    
    table_header_style = ParagraphStyle(
        'TableHeader',
        parent=styles['Normal'],
        fontSize=9,
        leading=12,
        textColor=colors.white,
        fontName='Helvetica-Bold'
    )
    
    table_cell_style = ParagraphStyle(
        'TableCell',
        parent=styles['Normal'],
        fontSize=9,
        leading=12,
        textColor=text_color
    )
    
    table_cell_bold = ParagraphStyle(
        'TableCellBold',
        parent=styles['Normal'],
        fontSize=9,
        leading=12,
        textColor=primary_color,
        fontName='Helvetica-Bold'
    )

    story = []
    
    # 1. Header Section
    story.append(Paragraph("ReturnRadar", title_style))
    story.append(Paragraph("AI-Powered Return Audit & Financial Impact Report", subtitle_style))
    story.append(Spacer(1, 10))
    
    # 2. Key Metrics Summary Panel
    metrics_data = [
        [
            Paragraph("<b>Total Capital Bled</b>", body_style),
            Paragraph("<b>Highest Bleeding Category</b>", body_style),
            Paragraph("<b>Total Returns Audited</b>", body_style)
        ],
        [
            Paragraph(f"<font size=16 color='#EF4444'><b>EGP {data['total_leak']:,.2f}</b></font>", body_style),
            Paragraph(f"<font size=12 color='#F59E0B'><b>{data['highest_bleeding_category']}</b></font>", body_style),
            Paragraph(f"<font size=14 color='#3B82F6'><b>{data['number_of_returns']}</b></font>", body_style)
        ]
    ]
    
    metrics_table = Table(metrics_data, colWidths=[2.3*inch, 3.1*inch, 2.1*inch])
    metrics_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), bg_light),
        ('ALIGN', (0,0), (-1,-1), 'LEFT'),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('PADDING', (0,0), (-1,-1), 12),
        ('LINEBELOW', (0,0), (-1,0), 0.5, colors.HexColor("#E2E8F0")),
        ('BOX', (0,0), (-1,-1), 1, colors.HexColor("#CBD5E1")),
    ]))
    
    story.append(metrics_table)
    story.append(Spacer(1, 15))
    
    # 3. Category Breakdown
    story.append(Paragraph("Category Financial Breakdown", heading2_style))
    category_rows = [
        [
            Paragraph("Category", table_header_style),
            Paragraph("Percentage", table_header_style),
            Paragraph("Total Capital Lost", table_header_style)
        ]
    ]
    
    for item in data['category_breakdown']:
        category_rows.append([
            Paragraph(item['category'], table_cell_bold),
            Paragraph(f"{item['percentage']:.1f}%", table_cell_style),
            Paragraph(f"EGP {item['total_value']:,.2f}", table_cell_style)
        ])
        
    category_table = Table(category_rows, colWidths=[3.5*inch, 1.5*inch, 2.5*inch])
    category_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), primary_color),
        ('ALIGN', (0,0), (-1,-1), 'LEFT'),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('PADDING', (0,0), (-1,-1), 8),
        ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, bg_light]),
        ('GRID', (0,0), (-1,-1), 0.5, colors.HexColor("#E2E8F0")),
    ]))
    story.append(category_table)
    story.append(Spacer(1, 15))
    
    # 4. Worst Performing SKUs (Actionable Insights)
    story.append(Paragraph("Worst Performing SKUs & Immediate Action Plans", heading2_style))
    
    for idx, sku_info in enumerate(data['worst_performing_skus'], 1):
        sku_details = [
            [Paragraph(f"<b>#{idx} SKU:</b> {sku_info['sku']}  |  <b>Product:</b> {sku_info['product_name']}", table_cell_bold)],
            [Paragraph(f"<b>Estimated Return Rate:</b> {sku_info['return_rate']:.1f}%", table_cell_style)],
            [Paragraph(f"<b>AI Insight:</b> {sku_info['insight']}", table_cell_style)],
            [Paragraph(f"<b>Immediate Recommended Action:</b> <font color='#EF4444'><b>{sku_info['action']}</b></font>", table_cell_style)]
        ]
        sku_table = Table(sku_details, colWidths=[7.5*inch])
        sku_table.setStyle(TableStyle([
            ('BACKGROUND', (0,0), (-1,-1), colors.white),
            ('ALIGN', (0,0), (-1,-1), 'LEFT'),
            ('VALIGN', (0,0), (-1,-1), 'TOP'),
            ('PADDING', (0,0), (-1,-1), 6),
            ('BOX', (0,0), (-1,-1), 1, colors.HexColor("#EF4444") if idx == 1 else colors.HexColor("#CBD5E1")),
            ('LINELEFT', (0,0), (-1,-1), 4, colors.HexColor("#EF4444") if idx == 1 else colors.HexColor("#94A3B8")),
        ]))
        story.append(KeepTogether([sku_table, Spacer(1, 10)]))
        
    # Build Document
    doc.build(story)
