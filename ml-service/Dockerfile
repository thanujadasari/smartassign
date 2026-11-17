FROM python:3.13-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /srv/ml-service
COPY ml-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY ml-service/app ./app
COPY ml-service/training/__init__.py ml-service/training/features.py ./training/
COPY model-artifacts /srv/model-artifacts
ENV SMARTASSIGN_MODEL_PATH=/srv/model-artifacts/suitability_model.joblib \
    SMARTASSIGN_MODEL_METADATA_PATH=/srv/model-artifacts/model_metadata.json
RUN useradd --system --uid 1002 mlsvc
USER mlsvc
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
