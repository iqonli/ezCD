"""Build an actual MIT test variable font from the compatible rectangle-glyph masters."""
from pathlib import Path
from fontTools.designspaceLib import DesignSpaceDocument, AxisDescriptor, SourceDescriptor
from fontTools.varLib import build
root=Path(__file__).resolve().parents[1]/'app/src/test/resources/font-fixtures'
doc=DesignSpaceDocument()
axis=AxisDescriptor();axis.name='Weight';axis.tag='wght';axis.minimum=300;axis.default=400;axis.maximum=700;doc.addAxis(axis)
for name,weight in [('Light',300),('Regular',400),('Medium',500),('Bold',700)]:
    source=SourceDescriptor();source.path=str(root/f'{name}.ttf');source.name=name;source.location={'Weight':weight}
    if weight==400:source.copyInfo=True;source.copyLib=True;source.copyFeatures=True
    doc.addSource(source)
font,_,_=build(doc)
for ident,value in [(1,'ezCD Variable Fixture'),(16,'ezCD Variable Fixture'),(2,'Regular'),(17,'Regular'),(4,'ezCD Variable Fixture Regular'),(6,'ezCDVariableFixture-Regular')]:
    font['name'].setName(value,ident,3,1,0x409)
font.save(root/'WeightVariable.ttf')
print('Generated WeightVariable.ttf with gvar/wght 300–700, default 400.')
