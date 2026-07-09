import os
import uuid
import shutil
import json
import pandas as pd
from typing import List, Dict, Any
from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import ValidationError
import google.generativeai as genai
from dotenv import load_dotenv

# Import our Pydantic models
from shared.models import (
    UploadResponse,
    AuditResponse,
    CategorySummary,
    SKUAnalysis
)

load_dotenv()

app = FastAPI(
    title="ReturnRadar API",
    description="Backend AI audit engine for categorizing e-commerce returns and leaks."
)

# CORS configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Adjust for production (e.g., frontend URLs)
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

UPLOAD_DIR = "/tmp/returnradar_uploads"
os.makedirs(UPLOAD_DIR, exist_ok=True)

# Configure Gemini
api_key = os.getenv("GEMINI_API_KEY") or os.getenv("API_KEY")
model_name = os.getenv("AI_MODEL", "gemini-1.5-flash")
overhead_cost = float(os.getenv("OVERHEAD_COST", "100"))

if api_key:
    genai.configure(api_key=api_key)
else:
    print("WARNING: GEMINI_API_KEY is not set. API will run in sandbox/mock mode.")

def analyze_batch_with_llm(batch_df: pd.DataFrame) -> Dict[str, Any]:
    """
    Sends a batch of returns to Google Gemini and returns parsed structured JSON.
    """
    if not api_key:
        # Generate graceful mock data if key is missing
        return generate_sandbox_mock(batch_df)

    # Format the data for the LLM
    records = []
    for _, row in batch_df.iterrows():
        records.append({
            "product_name": str(row.get("Product Name", "Unknown")),
            "sku": str(row.get("SKU", "Unknown")),
            "customer_notes": str(row.get("Customer Notes", "")),
            "order_value": float(row.get("Order Value", 0))
        })
    
    prompt = f"""
Analyze the following customer return data. 

DATA:
{json.dumps(records, indent=2)}

SYSTEM INSTRUCTIONS:
You are a senior e-commerce financial analyst. Analyze the provided customer return notes.
For each entry:
1. Categorize the true root cause into strictly one of these categories: [Sizing Issue, Product Defect, Misleading Description, Delayed Delivery, Customer Buyer's Remorse].
2. Extract the specific detail (e.g., "runs 2 sizes too small", "zipper broke").

Output a valid JSON object matching this exact schema:
{{
  "category_percentages": {{
    "Sizing Issue": float,
    "Product Defect": float,
    "Misleading Description": float,
    "Delayed Delivery": float,
    "Customer Buyer's Remorse": float
  }},
  "worst_performing_skus": [
    {{
      "sku": "string",
      "product_name": "string",
      "return_count": int,
      "insight": "string",
      "action": "string"
    }}
  ]
}}
Do NOT wrap your response in markdown code blocks (like ```json ... ```). Output raw valid JSON only.
"""
    try:
        model = genai.GenerativeModel(model_name)
        response = model.generate_content(prompt)
        text = response.text.strip()
        
        # Clean up any potential markdown formatting in case LLM ignored instructions
        if text.startswith("```"):
            lines = text.split("\n")
            if lines[0].startswith("```"):
                lines = lines[1:]
            if lines[-1].strip() == "```":
                lines = lines[:-1]
            text = "\n".join(lines).strip()
            
        return json.loads(text)
    except Exception as e:
        print(f"Gemini processing error: {e}. Falling back to rule-based parser.")
        return generate_rule_based_analysis(batch_df)

def generate_sandbox_mock(df: pd.DataFrame) -> Dict[str, Any]:
    """Generates a highly realistic ReturnRadar response when API key is not configured."""
    categories = ["Sizing Issue", "Product Defect", "Misleading Description", "Delayed Delivery", "Customer Buyer's Remorse"]
    percentages = [42.5, 23.0, 15.5, 12.0, 7.0]
    
    # Identify top SKUs dynamically from the uploaded CSV
    sku_counts = df["SKU"].value_counts().head(3)
    worst_skus = []
    
    insights = {
        0: "Sizing ran significantly smaller than average, causing 80% of returns for this item.",
        1: "Multiple reports of material tearing near seams during first wear.",
        2: "Product photo depicts a vibrant red but physical product is dark burgundy."
    }
    actions = {
        0: "Update size chart on product detail page and add a 'Runs Small' sizing warning.",
        1: "Halt shipping and initiate a quality control inspection with the manufacturer.",
        2: "Re-shoot product images under standard studio lighting to match actual item color."
    }
    
    for i, (sku, count) in enumerate(sku_counts.items()):
        prod_name = df[df["SKU"] == sku]["Product Name"].iloc[0] if "Product Name" in df.columns else sku
        worst_skus.append({
            "sku": str(sku),
            "product_name": str(prod_name),
            "return_count": int(count),
            "insight": insights.get(i, "High return rates driven by expectations mismatch."),
            "action": actions.get(i, "Review description and update dimensions.")
        })
        
    return {
        "category_percentages": dict(zip(categories, percentages)),
        "worst_performing_skus": worst_skus
    }

def generate_rule_based_analysis(df: pd.DataFrame) -> Dict[str, Any]:
    """Simple rule-based categorization as a rock-solid network/API failure fallback."""
    notes = df["Customer Notes"].fillna("").str.lower()
    total = len(df) or 1
    
    sizing = notes.str.contains("size|fit|tight|loose|small|large|short|long").sum()
    defect = notes.str.contains("broken|tear|rip|damage|faulty|defect|zipper|quality").sum()
    misleading = notes.str.contains("description|color|photo|wrong|expect|fake|different").sum()
    delivery = notes.str.contains("late|delay|slow|arrive|courier|delivery").sum()
    remorse = total - (sizing + defect + misleading + delivery)
    if remorse < 0: remorse = 0
    
    percentages = {
        "Sizing Issue": (sizing / total) * 100,
        "Product Defect": (defect / total) * 100,
        "Misleading Description": (misleading / total) * 100,
        "Delayed Delivery": (delivery / total) * 100,
        "Customer Buyer's Remorse": (remorse / total) * 100
    }
    
    # Normalize percentages to sum to exactly 100%
    total_percentage = sum(percentages.values()) or 1
    percentages = {k: (v / total_percentage) * 100 for k, v in percentages.items()}
    
    sku_counts = df["SKU"].value_counts().head(3)
    worst_skus = []
    for sku, count in sku_counts.items():
        prod_name = df[df["SKU"] == sku]["Product Name"].iloc[0] if "Product Name" in df.columns else sku
        worst_skus.append({
            "sku": str(sku),
            "product_name": str(prod_name),
            "return_count": int(count),
            "insight": "High returns triggered by customer dissatisfaction notes.",
            "action": "Investigate manufacturing and sizing precision immediately."
        })
        
    return {
        "category_percentages": percentages,
        "worst_performing_skus": worst_skus
    }

@app.post("/upload", response_model=UploadResponse)
async def upload_csv(file: UploadFile = File(...)):
    """
    Saves the return log CSV and issues a unique job_id.
    """
    if not file.filename.endswith('.csv'):
        raise HTTPException(status_code=400, detail="Only CSV files are allowed.")
        
    job_id = str(uuid.uuid4())
    file_path = os.path.join(UPLOAD_DIR, f"{job_id}.csv")
    
    try:
        with open(file_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to save file: {e}")
        
    return UploadResponse(job_id=job_id, message="File uploaded successfully. Proceed to analysis.")

@app.get("/process/{job_id}", response_model=AuditResponse)
async def process_job(job_id: str):
    """
    Processes the uploaded CSV return log.
    Handles batching if rows > 50, aggregates outcomes, and returns structured financial metrics.
    """
    file_path = os.path.join(UPLOAD_DIR, f"{job_id}.csv")
    if not os.path.exists(file_path):
        raise HTTPException(status_code=404, detail="Job ID not found or expired.")
        
    try:
        df = pd.read_csv(file_path)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Unable to parse CSV file: {e}")
        
    # Check for required columns
    required_cols = {"Product Name", "SKU", "Customer Notes", "Order Value"}
    missing = required_cols - set(df.columns)
    if missing:
        raise HTTPException(
            status_code=400, 
            detail=f"Invalid CSV template. Missing required columns: {', '.join(missing)}"
        )
        
    total_returns = len(df)
    if total_returns == 0:
        raise HTTPException(status_code=400, detail="The uploaded return log is empty.")
        
    # Clean and fill NA
    df["Order Value"] = pd.to_numeric(df["Order Value"], errors="coerce").fillna(0.0)
    df["Customer Notes"] = df["Customer Notes"].fillna("")
    
    # Calculate leak metrics
    refund_sum = df["Order Value"].sum()
    total_leak = refund_sum + (overhead_cost * total_returns)
    
    # Batch processing (batch size of 50 as requested)
    batch_size = 50
    batches = [df[i:i + batch_size] for i in range(0, total_returns, batch_size)]
    
    all_category_percentages = []
    worst_sku_aggregates = {}
    
    for batch_df in batches:
        result = analyze_batch_with_llm(batch_df)
        all_category_percentages.append((len(batch_df), result.get("category_percentages", {})))
        
        # Aggregate SKU counts and keep track of insights/actions
        for sku_data in result.get("worst_performing_skus", []):
            sku = sku_data.get("sku")
            if not sku: continue
            if sku not in worst_sku_aggregates:
                worst_sku_aggregates[sku] = {
                    "product_name": sku_data.get("product_name", "Unknown"),
                    "return_count": 0,
                    "insights": [],
                    "actions": []
                }
            worst_sku_aggregates[sku]["return_count"] += sku_data.get("return_count", 1)
            worst_sku_aggregates[sku]["insights"].append(sku_data.get("insight", ""))
            worst_sku_aggregates[sku]["actions"].append(sku_data.get("action", ""))

    # 1. Calculate weighted average of category percentages
    weighted_categories = {
        "Sizing Issue": 0.0,
        "Product Defect": 0.0,
        "Misleading Description": 0.0,
        "Delayed Delivery": 0.0,
        "Customer Buyer's Remorse": 0.0
    }
    
    for count, percentages in all_category_percentages:
        for cat in weighted_categories:
            weighted_categories[cat] += percentages.get(cat, 0.0) * (count / total_returns)
            
    # Normalize weighted categories
    total_p = sum(weighted_categories.values()) or 1
    category_breakdown = []
    for cat, p in weighted_categories.items():
        normalized_p = (p / total_p) * 100
        # Category total value matches its proportional share of total_leak
        total_value = (normalized_p / 100.0) * total_leak
        category_breakdown.append(CategorySummary(
            category=cat,
            percentage=round(normalized_p, 1),
            total_value=round(total_value, 2)
        ))
        
    highest_bleeding_category = max(category_breakdown, key=lambda x: x.total_value).category
    
    # 2. Compile Top 3 Worst Performing SKUs
    sorted_skus = sorted(
        worst_sku_aggregates.items(),
        key=lambda item: item[1]["return_count"],
        reverse=True
    )[:3]
    
    worst_skus_response = []
    for sku, info in sorted_skus:
        # Calculate return rate as percentage of total returns
        return_rate = (info["return_count"] / total_returns) * 100
        
        # Combine insights and actions
        insight = " ".join([i for i in info["insights"] if i][:1]) or "High frequency return rate noted."
        action = " ".join([a for a in info["actions"] if a][:1]) or "Review product specification."
        
        worst_skus_response.append(SKUAnalysis(
            sku=sku,
            product_name=info["product_name"],
            return_rate=round(return_rate, 1),
            insight=insight,
            action=action
        ))
        
    # In case we found less than 3 SKUs, fallback to reading dynamic frequency from entire dataset
    if len(worst_skus_response) < min(3, len(df["SKU"].unique())):
        existing_skus = {x.sku for x in worst_skus_response}
        remaining_skus = df["SKU"].value_counts().index
        for s in remaining_skus:
            if s not in existing_skus:
                p_name = df[df["SKU"] == s]["Product Name"].iloc[0]
                worst_skus_response.append(SKUAnalysis(
                    sku=str(s),
                    product_name=str(p_name),
                    return_rate=round((df["SKU"].value_counts()[s] / total_returns) * 100, 1),
                    insight="High rate of notes indicating overall sizing or description discrepancy.",
                    action="Review customer satisfaction data and optimize product listings."
                ))
                if len(worst_skus_response) == 3:
                    break

    # Construct and validate complete response
    try:
        response_data = AuditResponse(
            job_id=job_id,
            total_leak=round(total_leak, 2),
            highest_bleeding_category=highest_bleeding_category,
            number_of_returns=total_returns,
            category_breakdown=category_breakdown,
            worst_performing_skus=worst_skus_response[:3]
        )
        return response_data
    except ValidationError as e:
        raise HTTPException(status_code=500, detail=f"Response serialization failed: {e}")
