"""
SmartAssign internal suitability service.

Called ONLY by the Spring Boot backend (never by the browser) with eligible claim-handler candidates.
The model is loaded once at start-up from model-artifacts/; it is never trained at request time.

  POST /ml/predict      score eligible candidates
  GET  /ml/health       liveness + whether a model is loaded
  GET  /ml/model-info   internal model metadata (keep this service on an internal network)
"""
from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app import config
from app.model import ModelUnavailable
from app.schemas import ErrorResponse, HealthResponse, PredictRequest, PredictResponse
from app.service import SuitabilityService

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
log = logging.getLogger("smartassign.ml")


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.service = SuitabilityService.from_paths(config.MODEL_PATH, config.METADATA_PATH)
    if app.state.service.model is None:
        log.error("Suitability model NOT loaded: %s", app.state.service.load_error)
    yield


app = FastAPI(title="SmartAssign suitability service", version="1.0.0", lifespan=lifespan,
              docs_url="/ml/docs", openapi_url="/ml/openapi.json", redoc_url=None)


def _service(request: Request) -> SuitabilityService:
    return request.app.state.service


@app.exception_handler(RequestValidationError)
async def validation_error(_: Request, exc: RequestValidationError):
    errors = [{"loc": list(e.get("loc", [])), "msg": e.get("msg")} for e in exc.errors()]
    return JSONResponse(status_code=422, content=ErrorResponse(status="error", detail=errors).model_dump())


@app.exception_handler(ModelUnavailable)
async def model_unavailable(_: Request, exc: ModelUnavailable):
    return JSONResponse(status_code=503, content=ErrorResponse(status="unavailable", detail=str(exc)).model_dump())


@app.exception_handler(Exception)
async def unexpected(_: Request, exc: Exception):
    log.exception("prediction failed")
    return JSONResponse(status_code=500, content=ErrorResponse(status="error", detail="prediction failed").model_dump())


@app.get("/ml/health", response_model=HealthResponse)
def health(request: Request) -> HealthResponse:
    return _service(request).health()


@app.post("/ml/predict", response_model=PredictResponse,
          responses={422: {"model": ErrorResponse}, 503: {"model": ErrorResponse}})
def predict(body: PredictRequest, request: Request) -> PredictResponse:
    return _service(request).predict(body)


@app.get("/ml/model-info", responses={503: {"model": ErrorResponse}})
def model_info(request: Request) -> dict:
    return _service(request).model_info()
