import os
import streamlit as st
import pandas as pd
import requests
import sys

# Ensure backend and shared modules are importable
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from backend.pdf_generator import create_pdf

# Page configuration
st.set_page_config(
    page_title="ReturnRadar | AI Return Audit & Financial Leak Dashboard",
    page_icon="🎯",
    layout="wide",
    initial_sidebar_state="expanded"
)

# Custom Dark Mode styling and custom components CSS
st.markdown("""
<style>
    /* Dark Theme Core Styles */
    .stApp {
        background-color: #0F172A;
        color: #F8FAFC;
    }
    
    /* Header styling */
    .title-logo {
        font-size: 42px;
        font-weight: 800;
        background: linear-gradient(135deg, #3B82F6, #10B981);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
        margin-bottom: 5px;
    }
    
    .subtitle-header {
        font-size: 16px;
        color: #94A3B8;
        margin-bottom: 30px;
    }
    
    /* Custom Card Style */
    .metric-card {
        background-color: #1E293B;
        border: 1px solid #334155;
        border-radius: 12px;
        padding: 24px;
        box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
    }
    
    .metric-label {
        font-size: 14px;
        text-transform: uppercase;
        color: #94A3B8;
        font-weight: 600;
        letter-spacing: 0.05em;
    }
    
    .metric-val {
        font-size: 32px;
        font-weight: 700;
        margin-top: 8px;
    }
    
    .metric-sub {
        font-size: 12px;
        color: #64748B;
        margin-top: 4px;
    }
    
    /* SKU Card Style */
    .sku-card {
        background-color: #1E293B;
        border-left: 5px solid #EF4444;
        border-radius: 8px;
        padding: 16px;
        margin-bottom: 12px;
    }
    
    .sku-title {
        font-size: 16px;
        font-weight: 700;
        color: #F8FAFC;
    }
    
    .sku-rate {
        color: #F59E0B;
        font-weight: 600;
        font-size: 14px;
    }
    
    .sku-insight {
        color: #CBD5E1;
        font-size: 14px;
        margin-top: 8px;
    }
    
    .sku-action {
        color: #10B981;
        font-size: 14px;
        font-weight: 600;
        margin-top: 4px;
    }
</style>
""", unsafe_allow_html=True)

# Environment and service setup
BACKEND_URL = os.getenv("BACKEND_URL", "http://localhost:8000")

# Sidebar configurations
with st.sidebar:
    st.image("https://images.unsplash.com/photo-1551836022-d5d88e9218df?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.0.3", use_container_width=True)
    st.markdown("<h2 style='text-align: center; color: #3B82F6;'>ReturnRadar Control Panel</h2>", unsafe_allow_html=True)
    st.markdown("---")
    st.write("📈 **Active Tier:** Starter Freemium Hook")
    st.write("💸 **Audit Fee Overhead:** EGP 100 / return")
    st.write("🤖 **AI Model:** " + os.getenv("AI_MODEL", "gemini-1.5-flash"))
    
    st.markdown("---")
    st.markdown("### 📥 Download Sample Return CSV")
    # Provide dummy CSV for onboarding
    sample_df = pd.DataFrame([
        {"Product Name": "Oversized Denim Jacket", "SKU": "DNM-JKT-001", "Customer Notes": "Runs 2 sizes too small, returned to buy larger size.", "Order Value": 1250},
        {"Product Name": "Cotton Chino Pants", "SKU": "CHN-PNT-023", "Customer Notes": "The zipper was completely broken upon arrival.", "Order Value": 890},
        {"Product Name": "Leather Chelsea Boots", "SKU": "BTS-LTH-09", "Customer Notes": "The material tore at the heel during first wear. Very poor quality.", "Order Value": 2100},
        {"Product Name": "Minimalist Dial Watch", "SKU": "WCH-MIN-101", "Customer Notes": "Dial is much darker red than pictured online. Misleading description.", "Order Value": 1800},
        {"Product Name": "Oversized Denim Jacket", "SKU": "DNM-JKT-001", "Customer Notes": "Too small, fits tight on shoulders.", "Order Value": 1250},
        {"Product Name": "Linen Summer Dress", "SKU": "DRS-LIN-05", "Customer Notes": "Just changed my mind, decided I do not need it.", "Order Value": 1400}
    ])
    csv_bytes = sample_df.to_csv(index=False).encode('utf-8')
    st.download_button(
        label="Download Sample Return Log",
        data=csv_bytes,
        file_name="returnradar_sample_log.csv",
        mime="text/csv"
    )

# Header Section
st.markdown("<h1 class='title-logo'>ReturnRadar</h1>", unsafe_allow_html=True)
st.markdown("<p class='subtitle-header'>AI-Driven E-Commerce Return & Profit Leak Audit Engine</p>", unsafe_allow_html=True)

# File Upload Workflow
uploaded_file = st.file_uploader(
    "Drag and drop your e-commerce return log CSV here", 
    type=["csv"], 
    help="Must contain columns: Product Name, SKU, Customer Notes, and Order Value."
)

if uploaded_file is not None:
    # 1. Preview uploaded file
    df_preview = pd.read_csv(uploaded_file)
    st.write(f"📂 Loaded return log with **{len(df_preview)} records**.")
    
    with st.expander("🔍 View Raw Uploaded Return Records"):
        st.dataframe(df_preview.head(10), use_container_width=True)
        
    uploaded_file.seek(0) # Reset stream pointer
    
    # 2. Upload file to backend
    st.write("---")
    st.write("🚀 Initiating AI Audit Engine...")
    
    with st.spinner("Processing CSV through direct AI semantic clustering (this may take up to 20-30 seconds depending on dataset size)..."):
        try:
            files = {"file": (uploaded_file.name, uploaded_file.getvalue(), "text/csv")}
            upload_response = requests.post(f"{BACKEND_URL}/upload", files=files)
            
            if upload_response.status_code == 200:
                job_id = upload_response.json()["job_id"]
                
                # Retrieve processing results
                process_response = requests.get(f"{BACKEND_URL}/process/{job_id}")
                
                if process_response.status_code == 200:
                    data = process_response.json()
                    st.success("🎉 AI Return Audit Completed Successfully!")
                    
                    # 3. Main Dashboard UI Layout
                    # Row 1: Metrics
                    col1, col2, col3 = st.columns(3)
                    
                    with col1:
                        st.markdown(f"""
                        <div class='metric-card'>
                            <div class='metric-label'>Total Capital Bled</div>
                            <div class='metric-val' style='color:#EF4444;'>EGP {data['total_leak']:,.2f}</div>
                            <div class='metric-sub'>Refund values + EGP 100 overhead per return</div>
                        </div>
                        """, unsafe_allow_html=True)
                        
                    with col2:
                        st.markdown(f"""
                        <div class='metric-card'>
                            <div class='metric-label'>Highest Bleeding Category</div>
                            <div class='metric-val' style='color:#F59E0B;'>{data['highest_bleeding_category']}</div>
                            <div class='metric-sub'>Highest cumulative financial bleed trigger</div>
                        </div>
                        """, unsafe_allow_html=True)
                        
                    with col3:
                        st.markdown(f"""
                        <div class='metric-card'>
                            <div class='metric-label'>Returns Audited</div>
                            <div class='metric-val' style='color:#10B981;'>{data['number_of_returns']}</div>
                            <div class='metric-sub'>Total logs semantically mapped by LLM</div>
                        </div>
                        """, unsafe_allow_html=True)
                        
                    st.markdown("<br>", unsafe_allow_html=True)
                    
                    # Row 2: Category Breakdown Charts and SKU Analysis
                    col_left, col_right = st.columns([1, 1])
                    
                    with col_left:
                        st.subheader("📊 Category Distribution & Leak Share")
                        
                        # Prepare data for charts
                        breakdown_data = pd.DataFrame(data["category_breakdown"])
                        
                        # Display custom chart using Streamlit native charts
                        st.bar_chart(
                            data=breakdown_data,
                            x="category",
                            y="percentage",
                            color="#3B82F6",
                            use_container_width=True
                        )
                        
                        # Table overview
                        st.write("Detailed Category Share:")
                        st.dataframe(
                            breakdown_data.rename(columns={
                                "category": "Root Cause",
                                "percentage": "Share %",
                                "total_value": "Cumulative Leak (EGP)"
                            }),
                            use_container_width=True,
                            hide_index=True
                        )
                        
                    with col_right:
                        st.subheader("🔥 Top 3 Worst Performing SKUs (Severe Leaks)")
                        
                        for idx, sku_info in enumerate(data["worst_performing_skus"], 1):
                            border_color = "#EF4444" if idx == 1 else "#F59E0B" if idx == 2 else "#94A3B8"
                            st.markdown(f"""
                            <div class='sku-card' style='border-left-color: {border_color};'>
                                <div class='sku-title'>#{idx} SKU: {sku_info['sku']} | {sku_info['product_name']}</div>
                                <div class='sku-rate'>Est. Return Rate: {sku_info['return_rate']}% of all returns</div>
                                <div class='sku-insight'><b>AI Insight:</b> {sku_info['insight']}</div>
                                <div class='sku-action'><b>Immediate Action:</b> {sku_info['action']}</div>
                            </div>
                            """, unsafe_allow_html=True)
                            
                    # 4. Reporting Section & PDF Download
                    st.markdown("<br>", unsafe_allow_html=True)
                    st.markdown("---")
                    st.subheader("📋 Export Final Audit Materials")
                    
                    # Trigger local ReportLab PDF generation
                    pdf_filename = f"/tmp/ReturnRadar_Audit_{job_id}.pdf"
                    create_pdf(data, pdf_filename)
                    
                    with open(pdf_filename, "rb") as pdf_file:
                        pdf_data = pdf_file.read()
                        
                    st.download_button(
                        label="📥 Download Branded PDF Audit Report",
                        data=pdf_data,
                        file_name=f"ReturnRadar_Audit_Report_{job_id}.pdf",
                        mime="application/pdf"
                    )
                    
                    # Clean up temporary PDF
                    if os.path.exists(pdf_filename):
                        os.remove(pdf_filename)
                else:
                    st.error(f"Failed to process return log. Error detail: {process_response.json().get('detail', 'Unknown error')}")
            else:
                st.error(f"Failed to upload return log. Error detail: {upload_response.json().get('detail', 'Unknown error')}")
                
        except requests.exceptions.ConnectionError:
            st.error(f"Could not connect to the backend API at {BACKEND_URL}. Ensure your backend server is running via 'uvicorn backend.main:app --reload'")
            st.warning("🚨 Running in Sandbox Emulation mode as a fallback...")
            # Fallback to local rendering for testing UI directly in Streamlit!
            st.write("Showing Mock Demo Audit (Mock Backend Process)")
            from backend.main import generate_sandbox_mock
            mock_data = generate_sandbox_mock(df_preview)
            
            # Formulate full response structure matching backend
            refund_sum = df_preview["Order Value"].sum() if "Order Value" in df_preview.columns else 12500
            total_leak = refund_sum + (100 * len(df_preview))
            
            category_breakdown = []
            for cat, p in mock_data["category_percentages"].items():
                category_breakdown.append({
                    "category": cat,
                    "percentage": p,
                    "total_value": round((p / 100.0) * total_leak, 2)
                })
                
            mock_resp = {
                "total_leak": total_leak,
                "highest_bleeding_category": "Sizing Issue",
                "number_of_returns": len(df_preview),
                "category_breakdown": category_breakdown,
                "worst_performing_skus": [
                    {
                        "sku": x["sku"],
                        "product_name": x["product_name"],
                        "return_rate": round((x["return_count"] / len(df_preview)) * 100, 1),
                        "insight": x["insight"],
                        "action": x["action"]
                    } for x in mock_data["worst_performing_skus"]
                ]
            }
            
            # Display results identically!
            col1, col2, col3 = st.columns(3)
            with col1:
                st.metric("Total Capital Bled", f"EGP {mock_resp['total_leak']:,.2f}", delta="-12.5% vs Last Month")
            with col2:
                st.metric("Highest Bleeding Category", mock_resp["highest_bleeding_category"])
            with col3:
                st.metric("Returns Audited", mock_resp["number_of_returns"])
                
            col_l, col_r = st.columns(2)
            with col_l:
                st.bar_chart(pd.DataFrame(mock_resp["category_breakdown"]), x="category", y="percentage")
            with col_r:
                for idx, sku in enumerate(mock_resp["worst_performing_skus"], 1):
                    st.info(f"**#{idx} SKU: {sku['sku']} ({sku['product_name']})**\nReturn rate: {sku['return_rate']}%\n*Insight:* {sku['insight']}\n*Action:* {sku['action']}")
else:
    # Onboarding view
    st.info("💡 **Welcome to ReturnRadar!** Drop a return log CSV file above to begin scanning for capital leakages.")
    
    col_info1, col_info2, col_info3 = st.columns(3)
    with col_info1:
        st.markdown("""
        ### 🔄 1. Upload Logs
        Drop standard return CSV file dumps containing SKU, notes, values directly from your Shopify or WooCommerce system.
        """)
    with col_info2:
        st.markdown("""
        ### 🤖 2. Semantic Analysis
        Our advanced LLM parses unstructured 'Customer Notes' into highly categorized, actionable clusters.
        """)
    with col_info3:
        st.markdown("""
        ### 📊 3. Bleed Insights
        Visualize where capital is lost (sizing, defects, delays) and download corporate-ready PDF reports.
        """)
