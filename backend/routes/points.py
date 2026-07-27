from fastapi import APIRouter, Depends, HTTPException, status
from models import User
from auth.dependencies import get_current_user
from sqlmodel import Session, select
from database import engine
from pymodels import PointsView
router = APIRouter(prefix="/api/v1/points", tags=["Points"])

@router.get(
    "",
    summary="Get logged user points",
    description=(
        "Get current user points"
        "it returns exactly what amount of points you currently have"
    )
)
def get_points(session: User = Depends(get_current_user)) -> PointsView:
    return PointsView(points=session.points)