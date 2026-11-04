# Price Bridge UK — Advanced Architecture & AI Strategy (Backup Plan)

> **Document Status:** Reference & Backup Plan (To be implemented in later phases).  
> **Primary Baseline Plan:** Preserved in `plan.md`.

---

## 1. Executive Summary & Vision

This plan documents the enterprise architecture, UK retail market adaptation, and cutting-edge AI feature suite designed for Price Bridge when expanding into the United Kingdom or launching advanced AI capabilities.

---

## 2. UK Supermarket Dynamics & Pricing Structure

### A. Dual-Tier Loyalty Pricing
In the UK, shelf labels and receipts feature two distinct prices:
1. **Regular Shelf Price** (e.g. £3.80)
2. **Loyalty / Member Price** (e.g. **£2.50 Clubcard Price**, **Nectar Price**, **Asda Rewards**, **Lidl Plus**)

**Data Model Representation:**
```sql
CREATE TYPE loyalty_scheme_enum AS ENUM ('NONE', 'CLUBCARD', 'NECTAR', 'ASDA_REWARDS', 'LIDL_PLUS', 'MORE');

ALTER TABLE price_records ADD COLUMN loyalty_price_pence INT;
ALTER TABLE price_records ADD COLUMN loyalty_scheme loyalty_scheme_enum DEFAULT 'NONE';
ALTER TABLE price_records ADD COLUMN unit_price_pence INT NOT NULL;
ALTER TABLE price_records ADD COLUMN unit_metric VARCHAR(16) NOT NULL; -- e.g. '100g', 'kg', '100ml', 'L', 'wash'
```

### B. Format Differentiation (Express / Local vs. Superstore)
Tesco Express and Sainsbury's Local branches charge 8% to 12% higher prices than their large Superstore/Extra counterparts. Geofencing and store classifications strictly separate these branches to avoid skewing price comparisons.

### C. Legal Unit Pricing (CMA Guidelines)
Standardized pence-per-unit metrics (`p/100g`, `p/kg`, `p/wash`, `p/sheet`) must be automatically calculated and prominently ranked to prevent shrinkflation deceptions.

---

## 3. High-Value AI Feature Roadmap

### Feature 1: The Trolley Ear (In-Store Audio Whisperer)
- **Concept:** Shoppers wear earphones while walking through supermarket aisles.
- **Mechanism:** On-device camera scans barcodes in real-time -> queries `/v1/products/{id}/compare` -> triggers sub-100ms native Android TTS audio:
  > *"Lurpak 500g is £4.50 here. Skip it — Lidl across the street has it for £3.00 today."*

### Feature 2: The 2-Stop Pareto Basket (Split-Store Optimizer)
- **Concept:** Evaluates multi-item shopping lists to split purchases between discounters (Aldi/Lidl) for staples and supermarkets (Tesco/M&S) for favorites.
- **Optimization:** Minimizes total basket cost while constraining maximum walking distance between stores (e.g., within 800m). Calculates the net hourly savings rate for the extra 5-minute walk.

### Feature 3: AI Bounty Hunter (Self-Healing Crowdsource Flywheel)
- **Concept:** The backend detects gaps in its price knowledge graph (e.g. *"No price for Lurpak at Morrisons Peckham for 7 days"*).
- **Gamification:** Dispatches in-app micro-bounties to nearby users: *"Snap Lurpak at Morrisons in the next 30 mins to earn 50 Bridge Points!"*
- **Verification:** Instant on-device computer vision validation confirms the shelf label in under 300ms.

### Feature 4: Deceptive Multi-Buy & Shrinkflation Exposer
- **Concept:** Anomaly detection on historical unit prices.
- **Alerts:**
  - *"Deceptive Offer: '3 for £5' is actually 12p more expensive per 100g than the 1kg family pack on the bottom shelf."*
  - *"Shrinkflation Alert: Pack weight reduced from 500g to 420g with no price reduction (+19% real inflation)."*

### Feature 5: Yellow Sticker Radar (Reduced-to-Clear Clearance)
- **Concept:** Scans evening markdown yellow stickers at M&S, Sainsbury's, and Tesco.
- **Broadcast:** Generates live 3-hour expiry deal notifications for local budget shoppers.

### Feature 6: Cart Bridge (Ocado / Tesco.com Digital Importer)
- **Concept:** Users share their online shopping cart screenshot or link.
- **Processing:** Multimodal vision parses all 30+ items and maps them to local in-store prices, displaying total savings.

---

## 4. Large-Scale Architecture & Tech Stack

```text
Customer App (Android Kotlin Compose) ──┐
Admin Web (Next.js + MUI) ──────────────┼─ Go API Gateway ─ Go BFFs ─ Go Services
Store Portal ───────────────────────────┘                              ├─ PostgreSQL 16 + PostGIS + pgvector
                                                                       ├─ Redis Cluster 7 (Geohash cache <5ms)
                                                                       ├─ S3 Object Storage (Receipts/Evidence)
                                                                       └─ Async Outbox Queue
                                                                          └─ Python AI Worker (FastAPI)
                                                                             ├─ BaseReceiptExtractor Interface
                                                                             │  ├─ Gemini 2.0 Flash (Cloud API)
                                                                             │  └─ Qwen2-VL / Llama Vision (vLLM)
                                                                             ├─ UK VAT & Math Auditor
                                                                             ├─ pHash Deduplication
                                                                             └─ Outlier Isolation Forest
```

### AI Extractor Abstraction (Interface-Driven Strategy)
```python
# ai_worker/services/receipt_extractor.py
from abc import ABC, abstractmethod

class BaseReceiptExtractor(ABC):
    @abstractmethod
    async def extract_receipt(self, image_bytes: bytes) -> ExtractedReceipt:
        pass

class GeminiReceiptExtractor(BaseReceiptExtractor):
    """Ultra-low cost (~$0.0001/receipt) for launch & rapid iteration."""
    pass

class OpenSourceVLMExtractor(BaseReceiptExtractor):
    """Self-hosted Qwen2-VL / Llama 3.2 Vision on vLLM for data sovereignty & scale."""
    pass
```

---

## 5. 5-Layer Anti-Fraud Defense Pipeline

1. **Layer 1 (Physical Reality):** In-app camera only, GPS geofence matching, pHash image deduplication.
2. **Layer 2 (AI Cross-Examination):** Extracts store tax ID / VAT number, line items, and verifies mathematical consistency ($\sum \text{items} + \text{VAT} = \text{Total}$).
3. **Layer 3 (Statistical Outlier Engine):** Rejects prices deviating $>3\sigma$ from regional medians.
4. **Layer 4 (Corroboration & Reputation):** Requires 2+ independent reputable submissions to earn the "Community Corroborated" status.
5. **Layer 5 (Self-Healing Community Loop):** Neighboring shoppers verify or report discrepancies; fraudulent accounts are shadowbanned and their submissions rolled back.

---

## 6. External Data Ingestion & `/browser` Integration

The `/browser` subagent can be leveraged during setup and seeding to:
- Retrieve official UK supermarket vector logos (SVGs) and brand color tokens.
- Ingest verified store location rosters (Superstore vs. Local/Express) across UK councils.
- Pull official CMA unit-pricing schedules to guarantee legal compliance.

