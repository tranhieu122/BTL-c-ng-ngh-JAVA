-- English-language learning materials belong to the English category.
-- Keep the department filter as a guard so unrelated CNTT documents are untouched.
UPDATE documents
SET category_id = 5
WHERE department_id = 15
  AND category_id = 1
  AND status = 'PUBLISHED';
