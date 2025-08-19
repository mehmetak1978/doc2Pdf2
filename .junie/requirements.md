
- Make an utility java service class for;
  - getting the name of the template from the caller
  - Read the template document and find metadata keys
    - [@HEADER] is a metadata key, where @HEADER will be replaced with the input key called header.
    - Apply the same process for all metadata keys taking place in the template document with a similar format
  - Metadata keys and values will be input to the service
  - Put the values coming as input, into the correct metadata places in the document
  - reate a pdf file and save it under "pdfs" folder. The name of the output document will be an input parameter to the service
  - Update the main.java to call this service with a sample template file called "template1.docx" under the resources folder

Technical Requirements:
  - use "docx4j" library for converting docx to pdf file. 