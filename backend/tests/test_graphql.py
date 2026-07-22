async def test_graphql_uses_custom_context(client):
    response = await client.post(
        "/graphql",
        json={"query": "{ __typename }"},
    )

    assert response.status_code == 200
    assert response.json() == {"data": {"__typename": "Query"}}
