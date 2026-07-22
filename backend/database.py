from collections.abc import AsyncGenerator, Generator
from typing import Annotated

from fastapi import Depends
from sqlmodel import Session, create_engine

from settings import settings

engine = create_engine(settings.DATABASE_URL, echo=False)

def get_session() -> Generator[Session, None, None]:
    """Dependency de FastAPI que proporciona una sesión de BD."""
    with Session(engine) as session:
        yield session


async def get_graphql_session() -> AsyncGenerator[Session, None]:
    """Sesión para Strawberry, cuyo context getter requiere teardown asíncrono."""
    with Session(engine) as session:
        yield session


SessionDep = Annotated[Session, Depends(get_session)]
