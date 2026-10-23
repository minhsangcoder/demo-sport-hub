"""Deterministic QR image used only by the isolated browser tests."""
import sys
import qrcode
from qrcode.image.svg import SvgPathFillImage

qrcode.make(sys.argv[1], image_factory=SvgPathFillImage).save(sys.argv[2])
