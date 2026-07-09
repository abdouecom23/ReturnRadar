from pydantic import BaseModel, Field
from typing import List, Dict

class SKUAnalysis(BaseModel):
    sku: str = Field(..., description="The unique SKU identifier")
    product_name: str = Field(..., description="The name of the product")
    return_rate: float = Field(..., description="The return rate percentage for this SKU")
    insight: str = Field(..., description="AI-generated insight about why this SKU has high returns")
    action: str = Field(..., description="AI-recommended immediate action to resolve the issue")

class CategorySummary(BaseModel):
    category: str = Field(..., description="The name of the return category")
    percentage: float = Field(..., description="Percentage of returns falling into this category")
    total_value: float = Field(..., description="Total cost or capital lost in this category")

class AuditResponse(BaseModel):
    job_id: str = Field(..., description="The processed job unique identifier")
    total_leak: float = Field(..., description="Sum of refund order values + overhead cost per return")
    highest_bleeding_category: str = Field(..., description="The category with the largest financial drain")
    number_of_returns: int = Field(..., description="The total number of return entries processed")
    category_breakdown: List[CategorySummary] = Field(..., description="Financial and percentage breakdown by category")
    worst_performing_skus: List[SKUAnalysis] = Field(..., description="Top 3 SKUs that bleed the most capital")

class UploadResponse(BaseModel):
    job_id: str
    message: str
