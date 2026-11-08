UPDATE t_adj_calculation_line
SET deleted = 1
WHERE calculation_id = :calculationId
  AND deleted = 0 AND
  /*bone:tenant*/
