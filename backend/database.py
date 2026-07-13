from collections.abc import Generator
from typing import Annotated

from fastapi import Depends
from sqlmodel import Session, SQLModel, create_engine

from settings import settings

engine = create_engine(settings.DATABASE_URL, echo=False)


def create_db_and_tables() -> None:
    """Crea todas las tablas definidas en SQLModel (útil para desarrollo)."""
    SQLModel.metadata.create_all(engine)


def get_session() -> Generator[Session, None, None]:
    """Dependency de FastAPI que proporciona una sesión de BD."""
    with Session(engine) as session:
        yield session


SessionDep = Annotated[Session, Depends(get_session)]
