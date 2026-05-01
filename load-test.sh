while true; do
  # Generate a random number to ensure uniqueness
  RANDOM_ID=$RANDOM
  curl --user admin:admin123 -X POST http://localhost:8081/api/v1/policies \
  -H "Content-Type: application/json" \
  -d '{
    "policyNumber": "POL-'$RANDOM_ID'",
    "policyHolder": "John Doe",
    "coverageAmount": 50000,
    "startDate": "2026-05-01"
  }'
  sleep 0.5
done
