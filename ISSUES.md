- [ ] Strategies currently are a timing policy for display and seems to work until the last round; 7th round targetUtility has a large impact for closing the deal on suppliers terms, effectively rendering strategies non-existent on final settlement
  - Test with the current flow 
  1. price 120, payment 60, delivery 30, contract 24
  2. price 120, payment 60, delivery 21, contract 24
  3. price 120, payment 60, delivery 14, contract 24
  4. price 120, payment 60, delivery 14, contract 12
  5. price 120, payment 60, delivery 7, contract 12
  6. price 120, payment 60, delivery 7, contract 12
  7. price 120, payment 60, delivery 7, contract 12
- [ ] MESO offers can not be negotiated any further after Option 1, Option 2, Option 3 - ideally should be able to start negotiating from that round onwards
