from pydantic import BaseModel, EmailStr

class EmailMessage(BaseModel):
    id: str
    to: EmailStr
    subject: str
    body: str